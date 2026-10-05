package com.mikasrevs.newphone;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mikasrevs.newphone.network.message.PhoneModelMessage;
import com.mikasrevs.newphone.phone.model.PhoneModelRegistry;
import com.mikasrevs.newphone.server.data.PhoneNumberSavedData;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;

/** Save migration, two independent players, bounded sync, and Blender/Forge screen contracts. */
final class PhoneModelChecks {
    static void run() throws Exception {
        persistence(); packets(); assets();
        System.out.println("Phone model checks passed (legacy/default, per-player reload, bounded packets, screen UVs, poses, Blender sources).");
    }
    private static void persistence() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        CompoundTag legacy = new CompoundTag(), entry = new CompoundTag();
        ListTag entries = new ListTag();
        entry.putUUID("Player", a); entry.putString("Number", "4321"); entries.add(entry); legacy.put("Entries", entries);
        var data = PhoneNumberSavedData.load(legacy);
        require(data.getPhoneModel(a).equals("classic") && data.getPhoneModel(b).equals("classic"), "old/new player default changed");
        require(data.setPhoneModel(a, "model_02") && data.setPhoneModel(b, "model_05"), "valid selection rejected");
        require(!data.setPhoneModel(a, "../../fake") && !data.setPhoneModel(a, "MODEL_03"), "unknown client identifier accepted");
        require(!data.setPhoneModel(a, "model_02"), "duplicate request mutates state");
        require(data.getOrAssign(a).equals("4321") && data.findOwner("4321").equals(a), "cosmetic change replaced phone identity");
        var saved = data.save(new CompoundTag());
        require(saved.getList("Entries", 10).equals(entries), "model selection rewrote phone numbers");
        data = PhoneNumberSavedData.load(saved);
        require(data.getPhoneModel(a).equals("model_02") && data.getPhoneModel(b).equals("model_05"), "independent player choices lost on reload");
        require(data.setPhoneModel(a, "classic"), "Classic cannot be restored");
        data = PhoneNumberSavedData.load(data.save(new CompoundTag()));
        require(data.getPhoneModel(a).equals("classic") && data.getPhoneModel(b).equals("model_05"), "reset changed another player");
        var corrupt = new CompoundTag(); corrupt.putUUID("Player", a); corrupt.putString("Model", "removed_future_shell");
        var models = new ListTag(); models.add(corrupt); saved.put("PhoneModels", models);
        require(PhoneNumberSavedData.load(saved).getPhoneModel(a).equals("classic"), "unknown saved shell has no fallback");
        require(PhoneModelRegistry.get(null) == PhoneModelRegistry.CLASSIC, "null selection has no fallback");
    }
    private static void packets() {
        var message = new PhoneModelMessage(UUID.randomUUID(), "model_04");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PhoneModelMessage.encode(message, buffer);
            require(buffer.readableBytes() < 64, "cosmetic sync grew beyond UUID + identifier");
            require(PhoneModelMessage.decode(buffer).equals(message) && !buffer.isReadable(), "cosmetic packet round trip failed");
            buffer.clear(); buffer.writeUUID(message.playerId()); buffer.writeUtf("x".repeat(33));
            boolean rejected = false;
            try { PhoneModelMessage.decode(buffer); } catch (io.netty.handler.codec.DecoderException expected) { rejected = true; }
            require(rejected, "oversized model packet accepted");
        } finally { buffer.release(); }
    }
    private static void assets() throws Exception {
        Path root = Path.of("src/main/resources/assets/newphone");
        JsonObject original = read(root.resolve("models/item/z_phone.json"));
        var atlasSources = new HashSet<String>();
        for (var source : read(root.getParent().resolve("minecraft/atlases/blocks.json")).getAsJsonArray("sources"))
            atlasSources.add(source.getAsJsonObject().get("resource").getAsString());
        var ids = new HashSet<String>();
        require(PhoneModelRegistry.all().size() == 6, "expected Classic + five shells");
        for (var model : PhoneModelRegistry.all()) {
            require(ids.add(model.id()), "duplicate stable model ID");
            var preview = ImageIO.read(root.resolve(model.preview().getPath()).toFile());
            require(preview.getWidth() == 256 && preview.getHeight() == 288, "preview dimensions changed");
            var frame = ImageIO.read(root.resolve(model.frame().getPath()).toFile());
            require(frame.getWidth() == 900 && frame.getHeight() == 1600, "GUI frame changes content scaling");
            require((frame.getRGB(450, 800) >>> 24) == 0, "physical frame hides the live app GUI");
            if (model == PhoneModelRegistry.CLASSIC) continue;
            require(atlasSources.contains(model.texture().toString()), "shell texture is absent from Minecraft's item atlas");
            Path folder = root.resolve("models/phone/" + model.id());
            var json = read(folder.resolve("phone.json"));
            var overrides = json.getAsJsonArray("overrides");
            require(overrides.size() == original.getAsJsonArray("overrides").size(), "case/boot/call override missing");
            for (int i = 0; i < overrides.size(); i++) {
                var replacement = overrides.get(i).getAsJsonObject();
                var old = original.getAsJsonArray("overrides").get(i).getAsJsonObject();
                require(replacement.get("predicate").equals(old.get("predicate")), "existing screen/case/call predicate changed");
                require(Files.exists(root.resolve("models/" + replacement.get("model").getAsString().split(":")[1] + ".json")), "unreachable model override");
            }
            var before = read(root.resolve("models/item/z_phone_base.json"));
            var after = read(folder.resolve("phone_base.json"));
            require(before.get("display").equals(after.get("display")), "normal item transforms changed");
            try (var files = Files.list(folder)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var resource = read(file);
                    if (!resource.has("textures")) continue;
                    for (var texture : resource.getAsJsonObject("textures").entrySet()) {
                        String location = texture.getValue().getAsString();
                        require(!location.startsWith("newphone:") || Files.exists(root.resolve("textures/" + location.split(":")[1] + ".png")),
                                "missing texture: " + location);
                    }
                }
            }
            String obj = Files.readString(folder.resolve("phone.obj")).replace("\r\n", "\n");
            require(obj.contains("g phone_screen\n") && obj.contains("usemtl screen\n"), "dedicated runtime screen missing");
            int triangles = (int) obj.lines().filter(s -> s.startsWith("f ")).count();
            require(triangles >= 500 && triangles <= 2500, "shell exceeds geometry budget");
            String screen = obj.substring(obj.indexOf("g phone_screen\n") + 15).split("\ng ", 2)[0];
            var corners = new HashSet<Integer>();
            for (String line : screen.lines().filter(s -> s.startsWith("vt ")).toList()) {
                String[] xy = line.split(" "); double u = Double.parseDouble(xy[1]), v = Double.parseDouble(xy[2]);
                require(Math.abs(u - Math.round(u)) < 1e-6 && Math.abs(v - Math.round(v)) < 1e-6, "screen UV is not a rectangular corner");
                require(u >= -1e-6 && u <= 1.000001 && v >= -1e-6 && v <= 1.000001, "screen UV out of bounds");
                corners.add((int)Math.round(u) + 2 * (int)Math.round(v));
            }
            require(corners.size() == 4, "full-rectangle GUI UV missing");
            var source = Path.of("phone_models", model.id());
            require(Files.size(source.resolve(model.id() + ".blend")) > 100_000, "editable Blender source absent");
            var validation = read(source.resolve("validation.json"));
            require(validation.get("triangles").getAsInt() == triangles, "Forge export differs from validated Blender mesh");
            var atlas = ImageIO.read(root.resolve("textures/phone/" + model.id() + "/atlas.png").toFile());
            require(atlas.getWidth() == 512 && atlas.getHeight() == 512, "atlas is not game-sized");
            for (var object : validation.getAsJsonObject("objects").entrySet()) {
                var value = object.getValue().getAsJsonObject();
                require(value.get("non_manifold_edges").getAsInt() == 0 && value.get("degenerate_faces").getAsInt() == 0, "invalid Blender topology");
                require(value.get("signed_volume").getAsDouble() > 0, "inverted normals");
            }
        }
        // The call predicate changes only screen pixels. Held poses belong to the
        // renderers, so inventory, dropped items and frames inherit normal placement.
        try (var files = Files.walk(root.resolve("models"))) {
            var callModels = files.filter(p -> p.toString().endsWith("_call.json")).toList();
            require(callModels.size() == 36, "incomplete call/case/chassis matrix");
            for (Path file : callModels) {
                var call = read(file);
                require(!call.has("display"), "call animation leaks into item transforms: " + file);
                String parent = call.get("parent").getAsString();
                Path parentFile = root.resolve("models/" + parent.split(":")[1] + ".json");
                String expected = file.getFileName().toString().replace("_call.json", ".json");
                if (!expected.contains("_case_")) expected = expected.replace(".json", "_base.json");
                require(parentFile.equals(file.resolveSibling(expected)), "call loses its chassis or fitted case");
                require(read(parentFile).has("display"), "call parent has no normal item transforms");
                require(call.getAsJsonObject("textures").get("screen").getAsString().equals("newphone:item/z_phone_screen_5"),
                        "call lost its lit phone screen");
            }
        }
    }
    private static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path).replace("\uFEFF", "")).getAsJsonObject(); }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
