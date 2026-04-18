package org.owl.flux.config.model;

import java.util.LinkedHashMap;
import java.util.Map;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class MutedCommandsConfig {
    public Map<String, CommandRule> vanilla = defaultsVanilla();

    @Setting("message-commands")
    public Map<String, CommandRule> messageCommands = defaultsMessageCommands();

    @Setting("essentialsx-message-commands")
    public Map<String, CommandRule> essentialsxMessageCommands = defaultsEssentialsxMessageCommands();

    @ConfigSerializable
    public static final class CommandRule {
        public boolean enabled = true;
        public String description = "";

        public CommandRule() {
        }

        public CommandRule(boolean enabled, String description) {
            this.enabled = enabled;
            this.description = description == null ? "" : description;
        }
    }

    private static Map<String, CommandRule> defaultsVanilla() {
        Map<String, CommandRule> defaults = new LinkedHashMap<>();
        defaults.put("tell", new CommandRule(true, "Vanilla private message command"));
        defaults.put("msg", new CommandRule(true, "Vanilla /msg alias"));
        defaults.put("w", new CommandRule(true, "Vanilla /w alias"));
        defaults.put("me", new CommandRule(true, "Vanilla emote message command"));
        defaults.put("say", new CommandRule(true, "Vanilla broadcast command"));
        defaults.put("teammsg", new CommandRule(true, "Vanilla team chat command"));
        defaults.put("tm", new CommandRule(true, "Vanilla /teammsg alias"));
        return defaults;
    }

    private static Map<String, CommandRule> defaultsMessageCommands() {
        Map<String, CommandRule> defaults = new LinkedHashMap<>();
        defaults.put("pm", new CommandRule(true, "Generic private message command"));
        defaults.put("dm", new CommandRule(true, "Generic direct message alias"));
        defaults.put("whisper", new CommandRule(true, "Generic whisper command"));
        defaults.put("tell", new CommandRule(true, "Common tell command alias"));
        defaults.put("msg", new CommandRule(true, "Common message command alias"));
        defaults.put("reply", new CommandRule(true, "Reply to the last private message"));
        defaults.put("r", new CommandRule(true, "Reply shorthand alias"));
        defaults.put("m", new CommandRule(true, "Message shorthand alias"));
        defaults.put("mail", new CommandRule(true, "Generic mail command family"));
        defaults.put("message", new CommandRule(true, "Generic /message alias"));
        return defaults;
    }

    private static Map<String, CommandRule> defaultsEssentialsxMessageCommands() {
        Map<String, CommandRule> defaults = new LinkedHashMap<>();
        defaults.put("msg", new CommandRule(true, "EssentialsX /msg"));
        defaults.put("m", new CommandRule(true, "EssentialsX /m"));
        defaults.put("tell", new CommandRule(true, "EssentialsX /tell alias"));
        defaults.put("w", new CommandRule(true, "EssentialsX /w alias"));
        defaults.put("reply", new CommandRule(true, "EssentialsX /reply"));
        defaults.put("r", new CommandRule(true, "EssentialsX /r"));
        defaults.put("mail", new CommandRule(true, "EssentialsX /mail (including mail send)"));
        defaults.put("emsg", new CommandRule(true, "EssentialsX /emsg"));
        defaults.put("email", new CommandRule(true, "EssentialsX /email alias"));
        return defaults;
    }
}
