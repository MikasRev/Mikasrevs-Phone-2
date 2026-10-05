package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.security.PhoneSecurityStore;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.ZPhoneClient;
import net.minecraft.network.chat.Component;

public final class ZPhonePinSetupScreen extends AbstractZPhonePinScreen {
    private enum Stage { CREATE, CONFIRM, SAVED }

    private final boolean recoveryMode;
    private Stage stage = Stage.CREATE;
    private String first = "";
    private long returnAt;

    public ZPhonePinSetupScreen() {
        this(false);
    }

    public ZPhonePinSetupScreen(boolean recoveryMode) {
        super(Component.literal(PhoneSettingsStore.text("MPhone Güvenlik", "MPhone Security")));
        this.recoveryMode = recoveryMode;
    }

    @Override
    protected String heading() {
        return switch (stage) {
            case CREATE -> recoveryMode
                    ? PhoneSettingsStore.text("Yeni PIN", "New Passcode")
                    : PhoneSettingsStore.text("PIN Oluştur", "Create Passcode");
            case CONFIRM -> PhoneSettingsStore.text("PIN'i Doğrula", "Confirm Passcode");
            case SAVED -> recoveryMode
                    ? PhoneSettingsStore.text("PIN Değiştirildi", "Passcode Changed")
                    : PhoneSettingsStore.text("PIN Kaydedildi", "Passcode Saved");
        };
    }

    @Override
    protected String subtitle() {
        return switch (stage) {
            case CREATE -> recoveryMode
                    ? PhoneSettingsStore.text("4 haneli yeni PIN seç", "Choose a new 4-digit PIN")
                    : PhoneSettingsStore.text("4 haneli PIN seç", "Choose a 4-digit PIN");
            case CONFIRM -> PhoneSettingsStore.text("Aynı PIN'i tekrar gir", "Enter the same PIN again");
            case SAVED -> recoveryMode
                    ? PhoneSettingsStore.text("Kişiler ve mesajlar korundu", "Contacts and messages were kept")
                    : PhoneSettingsStore.text("MPhone korunuyor", "Your MPhone is protected");
        };
    }

    @Override
    protected void onPinComplete(String pin) {
        if (stage == Stage.CREATE) {
            first = pin;
            clearPin();
            stage = Stage.CONFIRM;
            return;
        }
        if (stage != Stage.CONFIRM) return;
        if (!first.equals(pin)) {
            clearPin();
            first = "";
            stage = Stage.CREATE;
            showStatus(PhoneSettingsStore.text("PIN'ler eşleşmedi", "PINs did not match"), 1400L);
            return;
        }
        if (PhoneSecurityStore.savePin(pin)) {
            PhoneSettingsStore.setLockScreenEnabled(true);
            PhoneSettingsStore.setPinlessUnlockMode(false);
            stage = Stage.SAVED;
            returnAt = System.currentTimeMillis() + 650L;
            showStatus(PhoneSettingsStore.text("Kaydedildi", "Saved"), 800L);
            return;
        }
        clearPin();
        first = "";
        stage = Stage.CREATE;
        showStatus(PhoneSettingsStore.text("PIN kaydedilemedi", "Could not save PIN"), 1400L);
    }

    @Override
    public void tick() {
        super.tick();
        if (stage != Stage.SAVED || returnAt <= 0L || System.currentTimeMillis() < returnAt || minecraft == null) {
            return;
        }
        if (recoveryMode) ZPhoneClient.unlockToHome();
        else PhoneNavigationState.backOrHome(minecraft);
    }
}
