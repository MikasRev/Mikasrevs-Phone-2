package com.mikasrevs.newphone;

import com.mikasrevs.newphone.server.data.PhoneCommunicationSavedData;
import com.mikasrevs.newphone.client.data.PhonePhotoStore;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.mikasrevs.newphone.client.gui.PhoneApp;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneHomeScreen;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import net.minecraft.nbt.CompoundTag;

/** Release checks; fixtures never touch an actual world. */
public final class PhoneReleaseChecks {
    public static void main(String[] args) throws Exception {
        partialCommunicationSave();
        var empty = PhoneCommunicationSavedData.load(new CompoundTag());
        require(!empty.canCleanVoiceArchive(),
                "empty persisted archive must not authorize deleting existing voice files");
        require(!PhoneCommunicationSavedData.load(empty.save(new CompoundTag())).canCleanVoiceArchive(),
                "reopening a recovered archive re-enabled destructive voice cleanup");
        networkImageDimensions();
        homeSearchPagination();
        System.out.println("Phone release checks passed (partial save recovery, voice preservation, image bounds, search pages).");
    }

    @SuppressWarnings("unchecked")
    private static void homeSearchPagination() throws Exception {
        var paginate = ZPhoneHomeScreen.class.getDeclaredMethod("paginateSearchResults", List.class);
        paginate.setAccessible(true);
        List<PhoneApp> all = List.of(PhoneApp.values());
        for (int count : new int[]{0, 1, 6, 7, 18, all.size()}) {
            List<PhoneApp> matches = all.subList(0, count);
            var pages = (List<List<PhoneApp>>) paginate.invoke(null, matches);
            require(!pages.isEmpty(), "empty search has no safe page");
            var reachable = new ArrayList<PhoneApp>();
            for (List<PhoneApp> page : pages) {
                require(page.size() <= 6, "search results exceed the six clickable home slots");
                reachable.addAll(page);
            }
            require(reachable.equals(matches), "search pagination hides, duplicates or reorders matching apps");
            if (count > 6) require(pages.size() > 1, "matches after the sixth app are unreachable");
        }
    }

    private static void networkImageDimensions() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] valid = output.toByteArray();
        byte[] oversized = valid.clone();
        ByteBuffer.wrap(oversized).putInt(16, 32768).putInt(20, 32768);
        var crc = new CRC32();
        crc.update(oversized, 12, 17);
        ByteBuffer.wrap(oversized).putInt(29, (int) crc.getValue());
        var field = PhonePhotoStore.class.getDeclaredField("PHOTO_BYTES");
        field.setAccessible(true);
        Map<?, ?> cache = (Map<?, ?>) field.get(null);
        PhonePhotoStore.receive("release-huge-image", oversized);
        require(!cache.containsKey("release-huge-image"), "tiny network image admitted a billion-pixel allocation");
        PhonePhotoStore.receive("release-valid-image", valid);
        require(cache.containsKey("release-valid-image"), "valid PNG was rejected");
        cache.remove("release-valid-image");
        output.reset();
        ImageIO.write(new BufferedImage(768, 432, BufferedImage.TYPE_INT_RGB), "jpeg", output);
        require(com.mikasrevs.newphone.network.PhoneImageLimits.accepts(output.toByteArray()), "normal phone JPEG was rejected");
        require(!com.mikasrevs.newphone.network.PhoneImageLimits.accepts(new byte[]{1, 2, 3}), "malformed image header was accepted");
    }

    private static void partialCommunicationSave() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Json", """
                {"messages":[null,{"id":"kept","body":"Keep this message"}],
                 "contacts":{"owner":["friend"]},"groups":{"damaged":null}}
                """);
        var data = PhoneCommunicationSavedData.load(tag);
        var snapshot = data.copySnapshot();
        require(snapshot.messages.size() == 1, "one null record discarded the healthy message archive");
        require("Keep this message".equals(snapshot.messages.get(0).body), "healthy message changed");
        require(snapshot.contacts.get("owner").contains("friend"), "healthy contacts were lost");
        var restored = PhoneCommunicationSavedData.load(data.save(new CompoundTag())).copySnapshot();
        require(restored.messages.size() == 1, "recovered message did not survive another save");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
