package shit.zen.settings.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import shit.zen.settings.Setting;
import shit.zen.settings.SettingVisibility;

public class StringSetting extends Setting<String> {
    public StringSetting(String name, String defaultValue) {
        super(name, defaultValue);
    }

    public StringSetting(String name, String defaultValue, SettingVisibility visibility) {
        super(name, defaultValue, visibility);
    }

    @Override
    public void save(JsonObject jsonObject) {
        jsonObject.addProperty(this.getName(), this.getValue());
    }

    @Override
    public void load(JsonElement jsonElement) {
        this.setValue(jsonElement.getAsString());
    }
}
