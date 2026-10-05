package com.mikasrevs.newphone.client.video;

import com.mikasrevs.newphone.NewPhone;
import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.network.PhoneNetwork;
import com.mikasrevs.newphone.network.message.PhoneVideoFrameDataMessage;
import com.mikasrevs.newphone.network.message.PhoneVideoFrameUploadMessage;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Forge 1.20.1 live-video transport used by MPhone video calls.
 *
 * The main framebuffer is first scaled by the GPU into a small 256x144 target. Only
 * that small texture is copied back to the CPU, then JPEG compression runs on a
 * low-priority worker thread. This keeps the video path from reading the full game
 * window several times per second.
 */
public final class PhoneVideoStreamClient {
    public enum CameraMode { BACK, FRONT }

    public static final int FRAME_WIDTH = PhoneVideoFrameUploadMessage.FRAME_WIDTH;
    public static final int FRAME_HEIGHT = PhoneVideoFrameUploadMessage.FRAME_HEIGHT;
    private static final long FULL_CAPTURE_INTERVAL_MS = 140L;
    private static final long MINI_CAPTURE_INTERVAL_MS = 240L;
    private static final long REMOTE_STALE_MS = 2_000L;
    private static final int FRONT_CAMERA_FOV = 30;

    private static final ResourceLocation REMOTE_TEXTURE_ID = new ResourceLocation(NewPhone.MOD_ID, "video/remote_call_frame");
    private static final ResourceLocation LOCAL_TEXTURE_ID = new ResourceLocation(NewPhone.MOD_ID, "video/local_call_frame");
    private static final AtomicBoolean CAPTURE_BUSY = new AtomicBoolean();
    private static final AtomicBoolean REMOTE_DECODE_BUSY = new AtomicBoolean();
    private static final AtomicLong NEXT_SEQUENCE = new AtomicLong();
    private static final AtomicLong STREAM_GENERATION = new AtomicLong();
    private static final AtomicLong REMOTE_GENERATION = new AtomicLong();
    private static final AtomicReference<PendingRemoteFrame> PENDING_REMOTE_FRAME = new AtomicReference<>();
    private static final ExecutorService ENCODE_EXECUTOR = Executors.newSingleThreadExecutor(
            new VideoThreadFactory("MPhone-Video-Encode")
    );
    private static final ExecutorService DECODE_EXECUTOR = Executors.newSingleThreadExecutor(
            new VideoThreadFactory("MPhone-Video-Decode")
    );

    private static volatile boolean cameraEnabled = true;
    private static volatile CameraMode cameraMode = CameraMode.FRONT;
    private static volatile long lastCaptureAt;
    private static volatile long latestRemoteSequence = -1L;
    private static volatile long latestRemoteFrameAt;
    private static volatile String latestRemoteSender = "";
    private static volatile boolean worldMini;

    private static String activeCallId = "";
    private static String publishedCallId = "";
    private static boolean cameraStatePublished;
    private static boolean publishedCameraEnabled;
    private static String publishedCameraMode = "";
    private static boolean lastPeerCameraEnabled = true;

    private static CameraType cameraBeforeVideo;
    private static Integer fovBeforeVideo;
    private static boolean cameraPerspectiveOverridden;
    private static TextureTarget captureTarget;
    private static DynamicTexture remoteTexture;
    private static DynamicTexture localTexture;
    private static int remoteWidth;
    private static int remoteHeight;
    private static int localWidth;
    private static int localHeight;

    private PhoneVideoStreamClient() { }

    public static boolean isCameraEnabled() { return cameraEnabled; }
    public static CameraMode cameraMode() { return cameraMode; }
    public static String cameraModeName() { return cameraMode.name(); }
    public static boolean isFrontCamera() { return cameraMode == CameraMode.FRONT; }
    public static ResourceLocation remoteTextureId() { return REMOTE_TEXTURE_ID; }
    public static ResourceLocation localTextureId() { return LOCAL_TEXTURE_ID; }
    public static int remoteWidth() { return remoteWidth; }
    public static int remoteHeight() { return remoteHeight; }
    public static int localWidth() { return localWidth; }
    public static int localHeight() { return localHeight; }

    public static void setCameraEnabled(boolean enabled) {
        cameraEnabled = enabled;
        lastCaptureAt = 0L;
        if (enabled && !worldMini) applySelectedPerspective();
        else {
            restoreGameplayPerspective();
            releaseLocalTexture();
        }
        publishCameraStateIfChanged();
    }

    public static void toggleCameraMode() {
        cameraMode = cameraMode == CameraMode.FRONT ? CameraMode.BACK : CameraMode.FRONT;
        lastCaptureAt = 0L;
        releaseLocalTexture();
        if (cameraEnabled && !worldMini) applySelectedPerspective();
        publishCameraStateIfChanged();
    }

    /** Called when a full-screen active video call becomes visible. */
    public static void prepareForVideoCall() {
        if (!PhoneClientData.isVideoCall() || !PhoneClientData.callActive()) return;
        ensureCallSession();
        if (worldMini) restoreGameplayPerspective();
        else if (cameraEnabled) applySelectedPerspective();
        boolean peerCameraEnabled = PhoneClientData.peerVideoCameraEnabled();
        if (!peerCameraEnabled) {
            if (lastPeerCameraEnabled) REMOTE_GENERATION.incrementAndGet();
            releaseRemoteTexture();
        }
        lastPeerCameraEnabled = peerCameraEnabled;
        publishCameraStateIfChanged();
    }

    /** Keeps video capture in background mode while the full call screen is hidden. */
    public static void enterWorldMiniMode() {
        if (!PhoneClientData.isVideoCall() || !PhoneClientData.hasCall()) return;
        ensureCallSession();
        worldMini = true;
        lastCaptureAt = 0L;
        restoreGameplayPerspective();
        releaseLocalTexture();
        publishCameraStateIfChanged();
    }

    /** Re-applies the phone camera when the full call screen is reopened. */
    public static void leaveWorldMiniMode() {
        worldMini = false;
        lastCaptureAt = 0L;
        prepareForVideoCall();
    }

    private static void ensureCallSession() {
        String callId = PhoneClientData.callId();
        if (callId == null || callId.isBlank() || callId.equals(activeCallId)) return;
        STREAM_GENERATION.incrementAndGet();
        PENDING_REMOTE_FRAME.set(null);
        restoreGameplayPerspective();
        releaseRemoteTexture();
        releaseLocalTexture();
        destroyCaptureTarget();
        activeCallId = callId;
        latestRemoteSequence = -1L;
        latestRemoteFrameAt = 0L;
        latestRemoteSender = "";
        lastPeerCameraEnabled = PhoneClientData.peerVideoCameraEnabled();
        lastCaptureAt = 0L;
        NEXT_SEQUENCE.set(0L);
        publishedCallId = "";
        publishedCameraMode = "";
        cameraStatePublished = false;
    }

    private static void applySelectedPerspective() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || worldMini || !PhoneClientData.isVideoCall()) return;
        if (!cameraPerspectiveOverridden) {
            cameraBeforeVideo = minecraft.options.getCameraType();
            fovBeforeVideo = minecraft.options.fov().get();
            cameraPerspectiveOverridden = true;
        }
        if (cameraMode == CameraMode.FRONT) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            minecraft.options.fov().set(FRONT_CAMERA_FOV);
        } else {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            if (fovBeforeVideo != null) minecraft.options.fov().set(fovBeforeVideo);
        }
    }

    private static void restoreGameplayPerspective() {
        if (!cameraPerspectiveOverridden) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.options != null) {
            if (cameraBeforeVideo != null) minecraft.options.setCameraType(cameraBeforeVideo);
            if (fovBeforeVideo != null) minecraft.options.fov().set(fovBeforeVideo);
        }
        cameraBeforeVideo = null;
        fovBeforeVideo = null;
        cameraPerspectiveOverridden = false;
    }

    private static void publishCameraStateIfChanged() {
        if (!PhoneClientData.isVideoCall() || !PhoneClientData.callActive()) return;
        String callId = PhoneClientData.callId();
        if (callId == null || callId.isBlank()) return;
        String effectiveMode = worldMini ? CameraMode.BACK.name() : cameraModeName();
        if (cameraStatePublished && callId.equals(publishedCallId)
                && cameraEnabled == publishedCameraEnabled && effectiveMode.equals(publishedCameraMode)) return;
        publishedCallId = callId;
        publishedCameraEnabled = cameraEnabled;
        publishedCameraMode = effectiveMode;
        cameraStatePublished = true;
        PhoneClientData.updateVideoCameraState(cameraEnabled, effectiveMode);
    }

    public static boolean hasFreshRemoteFrame(String peerUuid) {
        return remoteTexture != null && peerUuid != null && peerUuid.equals(latestRemoteSender)
                && System.currentTimeMillis() - latestRemoteFrameAt <= REMOTE_STALE_MS;
    }

    public static boolean hasLocalPreview() { return localTexture != null && cameraEnabled; }
    public static boolean isWorldMini() { return worldMini; }

    /** Capture must be called after the 3D world was rendered and before drawing the phone UI. */
    public static void captureIfDue(boolean worldMini) {
        ensureCallSession();
        if (!isStreamingCallActive()) {
            if (!PhoneClientData.hasCall()) clearCallTextures();
            return;
        }
        long now = System.currentTimeMillis();
        long interval = worldMini ? MINI_CAPTURE_INTERVAL_MS : FULL_CAPTURE_INTERVAL_MS;
        if (now - lastCaptureAt < interval || !CAPTURE_BUSY.compareAndSet(false, true)) return;
        lastCaptureAt = now;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getMainRenderTarget() == null) {
            CAPTURE_BUSY.set(false);
            return;
        }
        NativeImage captured = null;
        try {
            TextureTarget target = ensureCaptureTarget();
            var source = minecraft.getMainRenderTarget();
            GlStateManager._glBindFramebuffer(36008, source.frameBufferId);
            GlStateManager._glBindFramebuffer(36009, target.frameBufferId);
            GlStateManager._glBlitFrameBuffer(
                    0, 0, source.width, source.height,
                    0, 0, FRAME_WIDTH, FRAME_HEIGHT,
                    16384, 9729
            );
            source.bindWrite(true);

            captured = new NativeImage(FRAME_WIDTH, FRAME_HEIGHT, false);
            RenderSystem.bindTexture(target.getColorTextureId());
            captured.downloadTexture(0, false);
            captured.flipY();

            String peerUuid = PhoneClientData.callPeerUuid();
            String callId = PhoneClientData.callId();
            long sequence = NEXT_SEQUENCE.incrementAndGet();
            long generation = STREAM_GENERATION.get();
            if (!worldMini) installLocalFrame(callId, generation, copyImage(captured));
            NativeImage encodeImage = captured;
            ENCODE_EXECUTOR.execute(() -> encodeAndSend(minecraft, callId, peerUuid, sequence, generation, encodeImage));
            captured = null;
        } catch (RuntimeException exception) {
            try { minecraft.getMainRenderTarget().bindWrite(true); } catch (RuntimeException ignored) { }
            if (captured != null) captured.close();
            CAPTURE_BUSY.set(false);
        }
    }

    private static TextureTarget ensureCaptureTarget() {
        if (captureTarget == null || captureTarget.width != FRAME_WIDTH || captureTarget.height != FRAME_HEIGHT) {
            if (captureTarget != null) captureTarget.destroyBuffers();
            captureTarget = new TextureTarget(FRAME_WIDTH, FRAME_HEIGHT, false, Minecraft.ON_OSX);
        }
        return captureTarget;
    }

    private static void encodeAndSend(Minecraft minecraft, String callId, String peerUuid, long sequence,
                                      long generation, NativeImage image) {
        try (image) {
            BufferedImage buffered = new BufferedImage(FRAME_WIDTH, FRAME_HEIGHT, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < FRAME_HEIGHT; y++) {
                for (int x = 0; x < FRAME_WIDTH; x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int r = abgr & 0xFF;
                    int g = (abgr >>> 8) & 0xFF;
                    int b = (abgr >>> 16) & 0xFF;
                    buffered.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            byte[] jpeg = encodeJpeg(buffered);
            if (jpeg == null || jpeg.length == 0 || jpeg.length > PhoneVideoFrameUploadMessage.MAX_FRAME_BYTES) return;
            minecraft.execute(() -> {
                if (generation != STREAM_GENERATION.get() || !isStreamingCallActive()
                        || !callId.equals(PhoneClientData.callId()) || !peerUuid.equals(PhoneClientData.callPeerUuid())) return;
                PhoneNetwork.sendVideoFrameUpload(callId, peerUuid, sequence, FRAME_WIDTH, FRAME_HEIGHT, jpeg);
            });
        } catch (RuntimeException ignored) {
        } finally {
            CAPTURE_BUSY.set(false);
        }
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        for (float quality : new float[] {0.50F, 0.40F, 0.30F, 0.24F}) {
            byte[] data = encodeJpeg(image, quality);
            if (data != null && data.length <= PhoneVideoFrameUploadMessage.MAX_FRAME_BYTES) return data;
        }
        return null;
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) return null;
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(12_288);
             MemoryCacheImageOutputStream imageOutput = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutput.flush();
            return output.toByteArray();
        } catch (IOException ignored) {
            return null;
        } finally {
            writer.dispose();
        }
    }

    public static void receiveFrame(PhoneVideoFrameDataMessage message) {
        if (message == null || message.jpegData() == null || message.jpegData().length == 0) return;
        if (!PhoneClientData.isVideoCall() || !PhoneClientData.callActive()) return;
        ensureCallSession();
        if (!message.callId().equals(PhoneClientData.callId())
                || !message.senderUuid().equals(PhoneClientData.callPeerUuid())
                || message.sequence() <= latestRemoteSequence) return;
        if (!PhoneClientData.peerVideoCameraEnabled()) return;
        if (message.width() != FRAME_WIDTH || message.height() != FRAME_HEIGHT) return;
        latestRemoteSequence = message.sequence();
        PENDING_REMOTE_FRAME.set(new PendingRemoteFrame(
                STREAM_GENERATION.get(), REMOTE_GENERATION.get(), message.callId(), message.senderUuid(), message.sequence(),
                message.width(), message.height(), message.jpegData().clone()
        ));
        scheduleRemoteDecode();
    }

    private static void scheduleRemoteDecode() {
        if (REMOTE_DECODE_BUSY.compareAndSet(false, true)) {
            DECODE_EXECUTOR.execute(PhoneVideoStreamClient::decodePendingRemoteFrames);
        }
    }

    private static void decodePendingRemoteFrames() {
        try {
            PendingRemoteFrame frame;
            while ((frame = PENDING_REMOTE_FRAME.getAndSet(null)) != null) decodeRemoteFrame(frame);
        } finally {
            REMOTE_DECODE_BUSY.set(false);
            if (PENDING_REMOTE_FRAME.get() != null) scheduleRemoteDecode();
        }
    }

    private static void decodeRemoteFrame(PendingRemoteFrame frame) {
        if (frame.generation() != STREAM_GENERATION.get() || frame.remoteGeneration() != REMOTE_GENERATION.get()
                || !hasExpectedJpegDimensions(frame.jpegData(), frame.width(), frame.height())) return;
        NativeImage image = null;
        try {
            image = NativeImage.read(frame.jpegData());
            if (image.getWidth() != frame.width() || image.getHeight() != frame.height()) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) return;
            NativeImage decoded = image;
            minecraft.execute(() -> installRemoteFrame(frame, decoded));
            image = null;
        } catch (IOException | RuntimeException ignored) {
        } finally {
            if (image != null) image.close();
        }
    }

    private static boolean hasExpectedJpegDimensions(byte[] jpeg, int width, int height) {
        if (jpeg == null || jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) return false;
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(jpeg))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return reader.getWidth(0) == width && reader.getHeight(0) == height;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static void installRemoteFrame(PendingRemoteFrame frame, NativeImage image) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || frame.generation() != STREAM_GENERATION.get()
                    || frame.remoteGeneration() != REMOTE_GENERATION.get()
                    || frame.sequence() != latestRemoteSequence || !PhoneClientData.callActive()
                    || !PhoneClientData.isVideoCall() || !PhoneClientData.peerVideoCameraEnabled()
                    || !frame.callId().equals(PhoneClientData.callId())
                    || !frame.senderUuid().equals(PhoneClientData.callPeerUuid())) return;
            remoteTexture = updateTexture(minecraft, REMOTE_TEXTURE_ID, remoteTexture, image);
            image = null;
            remoteWidth = frame.width();
            remoteHeight = frame.height();
            latestRemoteSender = frame.senderUuid();
            latestRemoteFrameAt = System.currentTimeMillis();
        } catch (RuntimeException ignored) {
        } finally {
            if (image != null) image.close();
        }
    }

    private static void installLocalFrame(String callId, long generation, NativeImage image) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || generation != STREAM_GENERATION.get() || !cameraEnabled || worldMini
                    || !callId.equals(PhoneClientData.callId()) || !isStreamingCallActive()) return;
            localWidth = image.getWidth();
            localHeight = image.getHeight();
            localTexture = updateTexture(minecraft, LOCAL_TEXTURE_ID, localTexture, image);
            image = null;
        } catch (RuntimeException ignored) {
        } finally {
            if (image != null) image.close();
        }
    }

    private static boolean isStreamingCallActive() {
        return cameraEnabled && PhoneClientData.isVideoCall() && PhoneClientData.callActive()
                && !activeCallId.isBlank() && activeCallId.equals(PhoneClientData.callId())
                && PhoneClientData.callPeerUuid() != null && !PhoneClientData.callPeerUuid().isBlank();
    }

    private static NativeImage copyImage(NativeImage source) {
        NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), false);
        try {
            copy.copyFrom(source);
            return copy;
        } catch (RuntimeException exception) {
            copy.close();
            throw exception;
        }
    }

    private static DynamicTexture updateTexture(Minecraft minecraft, ResourceLocation textureId,
                                                DynamicTexture current, NativeImage image) {
        NativeImage pixels = current == null ? null : current.getPixels();
        if (pixels != null && pixels.getWidth() == image.getWidth() && pixels.getHeight() == image.getHeight()) {
            try {
                pixels.copyFrom(image);
                current.upload();
                return current;
            } finally {
                image.close();
            }
        }
        if (current != null) minecraft.getTextureManager().release(textureId);
        DynamicTexture replacement = null;
        try {
            replacement = new DynamicTexture(image);
            minecraft.getTextureManager().register(textureId, replacement);
            return replacement;
        } catch (RuntimeException exception) {
            if (replacement != null) replacement.close();
            throw exception;
        }
    }

    private static void releaseRemoteTexture() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && remoteTexture != null) {
            try { minecraft.getTextureManager().release(REMOTE_TEXTURE_ID); } catch (RuntimeException ignored) { }
        }
        remoteTexture = null;
        remoteWidth = 0;
        remoteHeight = 0;
        latestRemoteFrameAt = 0L;
        latestRemoteSender = "";
    }

    private static void releaseLocalTexture() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && localTexture != null) {
            try { minecraft.getTextureManager().release(LOCAL_TEXTURE_ID); } catch (RuntimeException ignored) { }
        }
        localTexture = null;
        localWidth = 0;
        localHeight = 0;
    }

    private static void destroyCaptureTarget() {
        if (captureTarget == null) return;
        try { captureTarget.destroyBuffers(); } catch (RuntimeException ignored) { }
        captureTarget = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getMainRenderTarget() != null) {
            try { minecraft.getMainRenderTarget().bindWrite(true); } catch (RuntimeException ignored) { }
        }
    }

    public static void clearCallTextures() {
        STREAM_GENERATION.incrementAndGet();
        REMOTE_GENERATION.incrementAndGet();
        PENDING_REMOTE_FRAME.set(null);
        restoreGameplayPerspective();
        releaseRemoteTexture();
        releaseLocalTexture();
        latestRemoteSequence = -1L;
        lastCaptureAt = 0L;
        worldMini = false;
        destroyCaptureTarget();
        cameraEnabled = true;
        cameraMode = CameraMode.FRONT;
        activeCallId = "";
        publishedCallId = "";
        publishedCameraMode = "";
        cameraStatePublished = false;
        lastPeerCameraEnabled = true;
    }

    private static final class VideoThreadFactory implements ThreadFactory {
        private final String name;

        private VideoThreadFactory(String name) {
            this.name = name;
        }

        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
            return thread;
        }
    }

    private record PendingRemoteFrame(long generation, long remoteGeneration, String callId, String senderUuid, long sequence,
                                      int width, int height, byte[] jpegData) { }
}
