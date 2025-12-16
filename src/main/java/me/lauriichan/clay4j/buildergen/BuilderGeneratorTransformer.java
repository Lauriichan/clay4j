package me.lauriichan.clay4j.buildergen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.Type;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaRecordSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MethodHolderSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.ParameterSource;
import org.jboss.forge.roaster.model.source.TypeHolderSource;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.lauriichan.maven.sourcemod.api.ISourceTransformer;
import me.lauriichan.maven.sourcemod.api.SourceTransformerUtils;

public class BuilderGeneratorTransformer implements ISourceTransformer {

    private static record CreateRef(String type, String constructor, boolean constructWithValue, String setter, String getter)
        implements IRef {}

    private static record ListRef(String add, String remove, String contains, String clear, String unmodifiableType,
        String unmodifiableMethod, boolean unique) implements IRef {}

    private static record FieldRef(String fieldName) implements IRef {}

    private static record BuilderRef(String builderMethod) implements IRef {}

    private static record Ignored() implements IRef {}

    private static final Ignored IGNORED = new Ignored();

    private static interface IRef {}

    private static class References extends Object2ObjectOpenHashMap<ParameterSource<?>, IRef> {
        private static final long serialVersionUID = 0L;

        @SuppressWarnings("unchecked")
        public <E extends IRef> Stream<Entry<ParameterSource<?>, E>> values(Class<E> type) {
            return object2ObjectEntrySet().stream().filter(entry -> type.isInstance(entry.getValue()))
                .map(entry -> (Entry<ParameterSource<?>, E>) entry);
        }

    }

    @Override
    public boolean canTransform(JavaSource<?> source) {
        return isValidSource(source) || hasNestedValidSource(source);
    }

    private boolean hasNestedValidSource(JavaSource<?> source) {
        if (source instanceof TypeHolderSource<?> typeHolder) {
            return typeHolder.getNestedTypes().stream().anyMatch(this::canTransform);
        }
        return false;
    }

    private boolean isValidSource(JavaSource<?> source) {
        return ((source.isClass() && !((JavaClassSource) source).isAbstract()) || source.isRecord())
            && source.hasAnnotation(GenerateBuilder.class);
    }

    @Override
    public void transform(JavaSource<?> dataSource) {
        ((TypeHolderSource<?>) dataSource).getNestedTypes().stream().filter(this::canTransform).forEach(this::transform);
        if (!isValidSource(dataSource)) {
            return;
        }
        AnnotationSource<?> builderAnnotation = dataSource.getAnnotation(GenerateBuilder.class);
        boolean isInternal = Objects.equals(builderAnnotation.getLiteralValue("internal"), "true");
        String builderName = builderAnnotation.getStringValue("name");
        if (builderName == null || builderName.isBlank()) {
            builderName = "builder";
        }
        String rootBuilderName = builderAnnotation.getStringValue("rootName");
        if (rootBuilderName != null && rootBuilderName.isBlank()) {
            rootBuilderName = null;
        }
        HashMap<String, String> defaultValues = new HashMap<>();
        List<? extends ParameterSource<?>> parameters = List.of();
        List<String> fieldNames = new ArrayList<>();
        fieldNames.add("this");
        if (dataSource instanceof JavaRecordSource recordSource) {
            parameters = recordSource.getRecordComponents();
            parameters.stream().map(ParameterSource::getName).forEach(fieldNames::add);
            SourceTransformerUtils.getFields(recordSource).stream().filter(field -> field.hasAnnotation(BuilderDefault.class))
                .forEach(field -> {
                    AnnotationSource<JavaRecordSource> annotation = field.getAnnotation(BuilderDefault.class);
                    String[] components = annotation.getStringArrayValue();
                    for (String component : components) {
                        defaultValues.put(component, field.getName());
                    }
                });
        } else if (dataSource instanceof JavaClassSource classSource) {
            classSource.getFields().stream().filter(field -> !field.isStatic()).map(FieldSource::getName).forEach(fieldNames::add);
            classSource.getFields().stream().filter(field -> field.hasAnnotation(BuilderDefault.class)).forEach(field -> {
                AnnotationSource<JavaClassSource> annotation = field.getAnnotation(BuilderDefault.class);
                String[] components = annotation.getStringArrayValue();
                for (String component : components) {
                    defaultValues.put(component, field.getName());
                }
            });
            List<MethodSource<JavaClassSource>> constructors = classSource.getMethods().stream().filter(method -> method.isConstructor())
                .toList();
            MethodSource<JavaClassSource> targetConstructor = constructors.get(0);
            int parameterAmount = targetConstructor.getParameters().size(), tmp;
            for (MethodSource<JavaClassSource> constructor : constructors) {
                tmp = constructor.getParameters().size();
                if (tmp > parameterAmount) {
                    parameterAmount = tmp;
                    targetConstructor = constructor;
                }
            }
            parameters = targetConstructor.getParameters();
        }

        if (!dataSource.hasImport(Objects.class)) {
            dataSource.addImport(Objects.class);
        }

        References references = new References();
        for (ParameterSource<?> parameter : parameters) {
            if (parameter.hasAnnotation(BuilderIgnore.class)) {
                references.put(parameter, IGNORED);
                continue;
            }
            if (parameter.hasAnnotation(FieldReference.class)) {
                String str = string(parameter.getAnnotation(FieldReference.class), "value", parameter.getName());
                if (!fieldNames.contains(str)) {
                    System.err.println("Couldn't find field reference '%s' for parameter '%s'".formatted(str, parameter.getName()));
                    continue;
                }
                references.put(parameter, new FieldRef(str));
                continue;
            }
            if (parameter.hasAnnotation(BuilderReference.class)) {
                references.put(parameter, new BuilderRef(string(parameter.getAnnotation(BuilderReference.class), "value", "builder")));
                continue;
            }
            if (parameter.hasAnnotation(ListReference.class)) {
                AnnotationSource<?> annotation = parameter.getAnnotation(ListReference.class);
                AnnotationSource<?> unmodifiable = annotation.getAnnotationValue("unmodifiable");
                references.put(parameter,
                    new ListRef(string(annotation, "add", "add"), string(annotation, "remove", "remove"),
                        string(annotation, "contains", "contains"), string(annotation, "clear", "clear"),
                        type(unmodifiable, "type", parameter.getType().getName()), string(unmodifiable, "method", null),
                        bool(annotation, "unique", true)));
                continue;
            }
            if (parameter.hasAnnotation(BuilderCreate.class)) {
                AnnotationSource<?> annotation = parameter.getAnnotation(BuilderCreate.class);
                String type = type(annotation, "type", null);
                if (type == null) {
                    continue;
                }
                references.put(parameter,
                    new CreateRef(type, string(annotation, "constructor", "new"), bool(annotation, "constructWithValue", false),
                        string(annotation, "setter", "set"), string(annotation, "getter", "get")));
                continue;
            }
        }

        JavaClassSource builderClass = Roaster.create(JavaClassSource.class).setName("Builder").setPublic().setFinal(true).setStatic(true);
        // Create Builder constructor
        MethodSource<JavaClassSource> builderConstructor = builderClass.addMethod().setConstructor(true).setPrivate();
        if (references.isEmpty()) {
            builderConstructor.setBody("");
        } else {
            StringBuilder builder = new StringBuilder();
            references.values(FieldRef.class).forEach(reference -> {
                String name = reference.getKey().getName();
                builderConstructor.addParameter(reference.getKey().getType().getName(), name);
                if (builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append("this.").append(name).append('=').append(name).append(';');
            });
            builderConstructor.setBody(builder.toString());
        }
        {
            // Create build() method
            // This also generates all getter and setter using generateBuilderField()
            StringBuilder builder = new StringBuilder();
            for (ParameterSource<?> parameter : parameters) {
                IRef ref = references.get(parameter);
                if (ref == IGNORED) {
                    continue;
                }
                generateBuilderField(dataSource, builderClass, defaultValues, parameter.getType(), parameter.getName(), ref);
                if (!builder.isEmpty()) {
                    builder.append(", ");
                }
                if (ref instanceof BuilderRef) {
                    builder.append("this.").append(parameter.getName()).append(".build()");
                } else if (ref instanceof ListRef listRef && listRef.unmodifiableMethod() != null) {
                    builder.append(listRef.unmodifiableType()).append('.').append(listRef.unmodifiableMethod()).append("(this.")
                        .append(parameter.getName()).append(')');
                } else {
                    builder.append("this.").append(parameter.getName());
                }
            }
            builderClass.addMethod().setName("build").setPublic()
                .setBody(builder.insert(0, "(").insert(0, dataSource.getName()).insert(0, "return new ").append(");").toString())
                .setReturnType(dataSource);
        }
        // Create builder() method
        if (dataSource instanceof MethodHolderSource<?> holder) {
            createBuilderMethod(holder.addMethod().setName(builderName).setReturnType(builderClass).setPublic(), references, isInternal);
            if (isInternal && rootBuilderName != null) {
                createBuilderMethod(holder.addMethod().setName(rootBuilderName).setReturnType(builderClass).setPublic(), references, false);
            }
        }
        // Add Builder type
        if (dataSource instanceof TypeHolderSource<?> holder) {
            holder.addNestedType(builderClass);
        }
    }

    private void createBuilderMethod(MethodSource<?> method, References references, boolean isInternal) {
        StringBuilder builder = new StringBuilder();
        references.values(FieldRef.class).forEach(reference -> {
            String name = reference.getKey().getName();
            String fieldValue = reference.getValue().fieldName();
            boolean isThis = fieldValue.equals("this");
            if (!isInternal && !isThis) {
                method.addParameter(reference.getKey().getType().getName(), name);
            }
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            if (isInternal) {
                if (!isThis) {
                    builder.append("this.");
                }
                builder.append(fieldValue);
            } else {
                if (isThis) {
                    builder.append("null");
                } else {
                    builder.append(name);
                }
            }
        });
        method.setBody(builder.insert(0, "return new Builder(").append(");").toString());
        if (!isInternal) {
            method.setStatic(true);
        }
    }

    // Generates field with its corresponding getter and setter including its default value
    private <O extends JavaSource<O>> void generateBuilderField(JavaSource<?> source, JavaClassSource builderClass,
        HashMap<String, String> defaultValues, Type<O> type, String name, IRef ref) {
        importType(source, type);
        String qualifiedType = type.getName();
        FieldSource<JavaClassSource> builderField = builderClass.addField().setName(name).setType(qualifiedType).setPrivate();
        if (ref != null) {
            switch (ref) {
            case BuilderRef builderRef -> {
                String typeName = qualifiedType;
                qualifiedType += ".Builder";
                builderField.setType(qualifiedType);
                builderField.setFinal(true);
                builderField.setStringInitializer("");
                builderField.setLiteralInitializer(
                    new StringBuilder().append(typeName).append('.').append(builderRef.builderMethod()).append("();").toString());
            }
            case FieldRef fieldRef -> {
                builderField.setFinal(true);
            }
            case ListRef listRef -> {
                qualifiedType = buildTypeNameWithGenerics(type);
                builderField.setType(qualifiedType);
                String argumentType = buildTypeNameWithGenerics(type.getTypeArguments().get(0));
                String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                MethodSource<JavaClassSource> adder = builderClass.addMethod().setPublic().setFinal(true);
                adder.setName("add" + suffix);
                adder.setReturnType(builderClass);
                adder.addParameter(argumentType, name);
                if (listRef.unique()) {
                    adder.setBody("""
                        if (this.%1$s.%3$s(%1$s)) {
                            return this;
                        }
                        this.%1$s.%2$s(%1$s);
                        return this;
                        """.formatted(name, listRef.add(), listRef.contains()));
                } else {
                    adder.setBody("""
                        this.%1$s.%2$s(%1$s);
                        return this;
                        """.formatted(name, listRef.add()));
                }
                MethodSource<JavaClassSource> remover = builderClass.addMethod().setPublic().setFinal(true);
                remover.setName("remove" + suffix);
                remover.setReturnType(builderClass);
                remover.addParameter(argumentType, name);
                remover.setBody("""
                    this.%1$s.%2$s(%1$s);
                    return this;
                    """.formatted(name, listRef.remove()));
                MethodSource<JavaClassSource> clearer = builderClass.addMethod().setPublic().setFinal(true);
                clearer.setName("clear" + suffix);
                clearer.setReturnType(builderClass);
                clearer.setBody("""
                    this.%1$s.%2$s();
                    return this;
                    """.formatted(name, listRef.clear()));
            }
            case CreateRef createRef -> {
                MethodSource<JavaClassSource> getter = builderClass.addMethod().setPublic().setFinal(true);
                getter.setName(name);
                getter.setReturnType(createRef.type);
                getter.setBody("""
                    return this.%1$s.%2$s();
                    """.formatted(name, createRef.getter()));
                if (createRef.setter() != null) {
                    MethodSource<JavaClassSource> setter = builderClass.addMethod().setPublic().setFinal(true);
                    setter.setName(name);
                    setter.setReturnType(builderClass);
                    setter.addParameter(createRef.type, name);
                    setter.setBody("""
                        this.%1$s.%2$s(%1$s);
                        return this;
                        """.formatted(name, createRef.setter()));
                }
                return;
            }
            default -> throw new IllegalArgumentException("Unexpected value: " + ref);
            }
        } else {
            String defaultParam = defaultValues.get(name);
            if (defaultParam != null) {
                builderField.setLiteralInitializer(defaultParam);
            }
            MethodSource<JavaClassSource> setter = builderClass.addMethod().setPublic().setFinal(true);
            setter.setName(name);
            setter.setReturnType(builderClass);
            setter.addParameter(qualifiedType, name);
            if (type.isPrimitive()) {
                setter.setBody("""
                    this.%1$s = %1$s;
                    return this;
                    """.formatted(name));
            } else if (defaultParam != null) {
                setter.setBody("""
                    if (%1$s == null) {
                        this.%1$s = %2$s;
                        return this;
                    }
                    this.%1$s = %1$s;
                    return this;
                    """.formatted(name, defaultParam));
            } else {
                setter.setBody("""
                    this.%1$s = Objects.requireNonNull(%1$s);
                    return this;
                    """.formatted(name));
            }
        }
        MethodSource<JavaClassSource> getter = builderClass.addMethod().setPublic().setFinal(true);
        getter.setName(name);
        getter.setReturnType(qualifiedType);
        getter.setBody("""
            return this.%1$s;
            """.formatted(name));
    }

    private void importType(JavaSource<?> source, Type<? extends JavaSource<?>> type) {
        if (type.isPrimitive() || type.getOrigin().getPackage().equals(source.getPackage()) || source.getName().equals(type.getName())) {
            return;
        }
        String typeName = buildTypeName(source, type);
        if (source.hasImport(typeName)) {
            return;
        }
        source.addImport(typeName);
    }

    private String buildTypeName(JavaSource<?> source, Type<? extends JavaSource<?>> type) {
        if (type.isPrimitive()) {
            return type.getName();
        }
        StringBuilder builder = new StringBuilder();
        String fullName = type.getQualifiedName();
        if (fullName.equals(type.getName())) {
            return fullName;
        }
        String typePackage = fullName.substring(0, fullName.length() - type.getName().length() - 1);
        if (typePackage.startsWith("java")) {
            return type.getName();
        }
        if (typePackage.isEmpty()) {
            return type.getName();
        }
        if (!typePackage.contains(".")) {
            return fullName;
        }
        Type<? extends JavaSource<?>> current = type;
        while (current.getParentType() != null) {
            builder.insert(0, type.getName());
            if (current != type) {
                builder.insert(0, '.');
            }
            current = current.getParentType();
        }
        if (typePackage.equals(source.getPackage())) {
            if (builder.isEmpty()) {
                return type.getName();
            }
            return builder.substring(1);
        }
        return builder.insert(0, typePackage).toString();
    }

    private String type(AnnotationSource<?> src, String name, String fallback) {
        Class<?> clz = src.getClassValue(name);
        if (clz == null || clz == Object.class) {
            return fallback;
        }
        return clz.getSimpleName();
    }

    private String string(AnnotationSource<?> src, String name, String fallback) {
        String str = src.getStringValue(name);
        if (str == null || str.isBlank()) {
            return fallback;
        }
        return str;
    }

    private boolean bool(AnnotationSource<?> src, String name, boolean fallback) {
        String str = src.getLiteralValue(name);
        if (str == null || str.isBlank()) {
            return fallback;
        }
        return Objects.equals(str, "true");
    }

    private String buildTypeNameWithGenerics(Type<?> type) {
        StringBuilder builder = new StringBuilder();
        for (Type<?> argumentType : type.getTypeArguments()) {
            if (builder.isEmpty()) {
                builder.append('<');
            } else {
                builder.append(", ");
            }
            builder.append(buildTypeNameWithGenerics(argumentType));
        }
        if (!builder.isEmpty()) {
            builder.append('>');
        }
        return builder.insert(0, type.getName()).toString();
    }

}
