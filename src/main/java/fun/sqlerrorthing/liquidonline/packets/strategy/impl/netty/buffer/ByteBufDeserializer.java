package fun.sqlerrorthing.liquidonline.packets.strategy.impl.netty.buffer;

import fun.sqlerrorthing.liquidonline.packets.strategy.impl.netty.buffer.data.BufferDeserializer;
import fun.sqlerrorthing.liquidonline.packets.strategy.impl.netty.buffer.data.context.BufferDeserializationContext;
import fun.sqlerrorthing.liquidonline.packets.strategy.impl.netty.buffer.wrappers.ByteBufReader;
import fun.sqlerrorthing.liquidonline.packets.strategy.impl.netty.compilertime.UnsignedNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Supplier;

public class ByteBufDeserializer {
    @NotNull
    private final Map<Class<?>, BufferDeserializer<?>> deserializers;

    @NotNull
    private final BufferDeserializationContext context;

    public ByteBufDeserializer(@NotNull Map<Class<?>, BufferDeserializer<?>> deserializers) {
        this.deserializers = deserializers;
        this.context = ByteBufDeserializer.this::deserialize;
    }

    @NotNull
    public <T> T deserialize(@NotNull ByteBufReader reader, @NotNull Class<T> clazz) throws IOException {
        try {
            T obj = clazz.getDeclaredConstructor().newInstance();

            for (var field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                var value = readFieldValue(reader, field);
                field.set(obj, value);
            }

            return obj;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IOException(e);
        }
    }

    @Nullable
    private Object readFieldValue(@NotNull ByteBufReader buf, @NotNull Field field) throws IOException {
        Class<?> type = field.getType();

        if (field.getGenericType() instanceof ParameterizedType parameterizedType) {
            return readValue(buf, type, field.getDeclaredAnnotations(), parameterizedType.getActualTypeArguments(), type.componentType());
        } else {
            return readValue(buf, type, field.getDeclaredAnnotations(), null, type.componentType());
        }
    }

    @Nullable
    private Object readValue(
            @NotNull ByteBufReader buf,
            @NotNull Class<?> type,
            @NotNull Annotation[] annotations,
            @Nullable Type[] actualTypeArguments,
            @Nullable Class<?> componentType
    ) throws IOException {
        if (buf.peekIsNullMarker()) {
            buf.skipByte();
            return null;
        }

        if (type == String.class) {
            return buf.readString();
        } else if (type == Boolean.class || type == boolean.class) {
            return buf.readBoolean();
        } else if (type.isEnum()) {
            @SuppressWarnings("unchecked")
            Class<Enum<?>> enumClass = (Class<Enum<?>>) type;
            int ordinal = buf.readUnsignedVarInt();
            return enumClass.getEnumConstants()[ordinal];
        } else if (type == Long.class || type == long.class) {
            return readAsUnsignedIfUnsignedAnnotationPreset(
                    buf::readUnsignedVarLong,
                    buf::readLong,
                    annotations
            );
        } else if (type == Integer.class || type == int.class) {
            return readAsUnsignedIfUnsignedAnnotationPreset(
                    buf::readUnsignedVarInt,
                    buf::readInt,
                    annotations
            );
        } else if (type == Short.class || type == short.class) {
            return readAsUnsignedIfUnsignedAnnotationPreset(
                    buf::readUnsignedVarShort,
                    buf::readShort,
                    annotations
            );
        } else if (type == Byte.class || type == byte.class) {
            return buf.readByte();
        } else if (type == Double.class || type == double.class) {
            return buf.readDouble();
        } else if (type == Float.class || type == float.class) {
            return buf.readFloat();
        }

        if (type.isArray()) {
            assert componentType != null;
            if (componentType == byte.class || componentType == Byte.class) {
                var length = buf.readUnsignedVarInt();
                var bytes = new byte[length];
                buf.readBytes(bytes);
                return bytes;
            } else {
                throw new UnsupportedOperationException("Not implemented yet.");
            }
        }

        if (List.class.isAssignableFrom(type)) {
            return readList(buf, actualTypeArguments);
        }

        BufferDeserializer<?> deserializer = getDeserializer(type);
        if (deserializer != null) {
            return deserializer.deserialize(buf, type, context);
        }

        return deserialize(buf, type);
    }

    private <T> T readAsUnsignedIfUnsignedAnnotationPreset(
            Supplier<T> readUnsigned,
            Supplier<T> readSigned,
            Annotation[] annotations
    ) {
        Optional<UnsignedNumber> unsigned = Arrays.stream(annotations)
                .filter(a -> a.annotationType().equals(UnsignedNumber.class))
                .map(a -> (UnsignedNumber) a)
                .findFirst();

        if (unsigned.isPresent()) {
            return readUnsigned.get();
        } else {
            return readSigned.get();
        }
    }

    private Object readList(ByteBufReader buf, Type[] actualTypeArguments) throws IOException {
        Type listType = actualTypeArguments[0];
        int size = buf.readUnsignedVarInt();

        List<Object> list = new ArrayList<>(size);

        for (var i = 0; i < size; i++) {
            Object element;

            if (listType instanceof ParameterizedType parameterizedType) {
                var rawType = (Class<?>) parameterizedType.getRawType();
                element = readValue(buf, rawType, new Annotation[0], parameterizedType.getActualTypeArguments(), rawType.componentType());
            } else {
                var rawType = (Class<?>) listType;
                element = readValue(buf, rawType, new Annotation[0], null, rawType.componentType());
            }

            list.add(element);
        }

        return list;
    }

    private BufferDeserializer<?> getDeserializer(Class<?> clazz) {
        BufferDeserializer<?> adapter = deserializers.get(clazz);
        if (adapter != null) {
            return adapter;
        }

        for (Class<?> superClass = clazz.getSuperclass(); superClass != null; superClass = superClass.getSuperclass()) {
            adapter = deserializers.get(superClass);
            if (adapter != null) {
                return adapter;
            }
        }

        for (Class<?> iface : clazz.getInterfaces()) {
            adapter = deserializers.get(iface);
            if (adapter != null) {
                return adapter;
            }
        }

        return null;
    }
}
