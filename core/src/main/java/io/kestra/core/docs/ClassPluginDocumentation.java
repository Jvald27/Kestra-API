package io.kestra.core.docs;

import io.kestra.core.plugins.PluginClassAndMetadata;
import lombok.*;

import java.util.*;

@Getter
@EqualsAndHashCode
@ToString
public class ClassPluginDocumentation<T> extends AbstractClassDocumentation<T> {
    private String icon;
    private String group;
    protected String docLicense;
    private String pluginTitle;
    private String subGroup;
    private String replacement;
    private List<MetricDoc> docMetrics;
    private Map<String, Object> outputs = new TreeMap<>();
    private Map<String, Object> outputsSchema;

    @SuppressWarnings("unchecked")
    private ClassPluginDocumentation(JsonSchemaGenerator jsonSchemaGenerator, PluginClassAndMetadata<T> plugin, boolean allProperties) {
        super(jsonSchemaGenerator, plugin.type(), allProperties ? null : plugin.baseClass());

        // plugins metadata
        Class<? extends T> cls = plugin.type();

        this.cls = plugin.alias() == null ? cls.getName() : plugin.alias();
        this.group = plugin.group();
        this.docLicense = plugin.license();
        this.pluginTitle = plugin.title();
        this.icon = plugin.icon();
        if (plugin.alias() != null) {
            replacement = cls.getName();
        }

        if (this.group != null && cls.getPackageName().startsWith(this.group) && cls.getPackageName().length() > this.group.length() && cls.getPackageName().charAt(this.group.length()) == '.') {
            this.subGroup = cls.getPackageName().substring(this.group.length() + 1);
        }

        this.shortName = plugin.alias() == null ? cls.getSimpleName() : plugin.alias().substring(plugin.alias().lastIndexOf('.') + 1);

        // outputs
        this.outputsSchema = jsonSchemaGenerator.outputs(allProperties ? null : plugin.baseClass(), cls);

        if (this.outputsSchema.containsKey("$defs")) {
            this.defs.putAll((Map<String, Object>) this.outputsSchema.get("$defs"));
            this.outputsSchema.remove("$defs");
        }

        if (this.outputsSchema.containsKey("properties")) {
            this.outputs = flatten(properties(this.outputsSchema), required(this.outputsSchema));
        }

        // metrics
        if (this.propertiesSchema.containsKey("$metrics")) {
            List<Map<String, Object>> metrics = (List<Map<String, Object>>) this.propertiesSchema.get("$metrics");

            this.docMetrics = metrics
                .stream()
                .map(r -> new MetricDoc(
                    (String) r.get("name"),
                    (String) r.get("type"),
                    (String) r.get("unit"),
                    (String) r.get("description")
                ))
                .toList();
        }

        if (plugin.alias() != null) {
            this.deprecated = true;
        }
    }

    public static <T> ClassPluginDocumentation<T> of(JsonSchemaGenerator jsonSchemaGenerator, PluginClassAndMetadata<T> plugin, boolean allProperties) {
        return new ClassPluginDocumentation<>(jsonSchemaGenerator, plugin, allProperties);
    }

    @AllArgsConstructor
    @Getter
    public static class MetricDoc {
        String name;
        String type;
        String unit;
        String description;
    }
}

