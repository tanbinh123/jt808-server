package org.yzh.framework.orm;

import org.yzh.framework.orm.annotation.Field;
import org.yzh.framework.orm.annotation.Fs;
import org.yzh.framework.orm.annotation.Message;
import org.yzh.framework.orm.model.DataType;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.*;

/**
 * BeanMetadata加载策略
 * @author yezhihao
 * @home https://gitee.com/yezhihao/jt808-server
 */
public abstract class LoadStrategy {

    public abstract <T> Map<Integer, BeanMetadata<T>> getBeanMetadata(Class<T> typeClass);

    public abstract BeanMetadata getBeanMetadata(Object typeId, Integer version);

    public abstract <T> BeanMetadata<T> getBeanMetadata(Class<T> typeClass, Integer version);

    protected static boolean loadTypeMapping(Map<Object, Class<?>> typeIdMapping, Class<?> messageClass) {
        Message type = messageClass.getAnnotation(Message.class);
        if (type != null) {
            int[] values = type.value();
            for (int value : values)
                typeIdMapping.put(value, messageClass);
            return true;
        }
        return false;
    }

    protected static void loadBeanMetadata(Map<String, Map<Integer, BeanMetadata<?>>> root, Class<?> typeClass) {
        Map<Integer, BeanMetadata<?>> beanMetadataMap = root.get(typeClass.getName());
        //不支持循环引用
        if (beanMetadataMap != null)
            return;

        List<PropertyDescriptor> properties = findFieldProperties(typeClass);
        if (properties.isEmpty())
            return;

        root.put(typeClass.getName(), beanMetadataMap = new HashMap(4));

        Map<Integer, List<BasicField>> multiVersionFields = findMultiVersionFields(root, properties);
        for (Map.Entry<Integer, List<BasicField>> entry : multiVersionFields.entrySet()) {

            Integer version = entry.getKey();
            List<BasicField> fieldList = entry.getValue();

            BasicField[] fields = fieldList.toArray(new BasicField[fieldList.size()]);
            Arrays.sort(fields);

            BeanMetadata beanMetadata = new BeanMetadata(typeClass, version, fields);
            beanMetadataMap.put(version, beanMetadata);
        }
    }

    protected static List<PropertyDescriptor> findFieldProperties(Class<?> typeClass) {
        BeanInfo beanInfo;
        try {
            beanInfo = Introspector.getBeanInfo(typeClass);
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }
        PropertyDescriptor[] properties = beanInfo.getPropertyDescriptors();
        List<PropertyDescriptor> result = new ArrayList<>(properties.length);

        for (PropertyDescriptor property : properties) {
            Method readMethod = property.getReadMethod();

            if (readMethod != null) {
                if (readMethod.isAnnotationPresent(Fs.class) || readMethod.isAnnotationPresent(Field.class)) {
                    result.add(property);
                }
            }
        }
        return result;
    }

    protected static Map<Integer, List<BasicField>> findMultiVersionFields(Map<String, Map<Integer, BeanMetadata<?>>> root, List<PropertyDescriptor> properties) {
        Map<Integer, List<BasicField>> multiVersionFields = new TreeMap<Integer, List<BasicField>>() {
            @Override
            public List<BasicField> get(Object key) {
                List result = super.get(key);
                if (result == null)
                    super.put((Integer) key, result = new ArrayList<>(properties.size()));
                return result;
            }
        };

        for (PropertyDescriptor property : properties) {
            Method readMethod = property.getReadMethod();

            Field field = readMethod.getDeclaredAnnotation(Field.class);
            if (field != null) {
                fillField(root, multiVersionFields, property, field);
            } else {
                Field[] fields = readMethod.getDeclaredAnnotation(Fs.class).value();
                for (int i = 0; i < fields.length; i++)
                    fillField(root, multiVersionFields, property, fields[i]);
            }
        }
        return multiVersionFields;
    }

    protected static void fillField(Map<String, Map<Integer, BeanMetadata<?>>> root, Map<Integer, List<BasicField>> multiVersionFields, PropertyDescriptor propertyDescriptor, Field field) {
        Class<?> typeClass = propertyDescriptor.getPropertyType();
        Method readMethod = propertyDescriptor.getReadMethod();

        BasicField value;
        int[] versions = field.version();

        if (field.type() == DataType.OBJ || field.type() == DataType.LIST) {
            if (Collection.class.isAssignableFrom(typeClass))
                typeClass = (Class<?>) ((ParameterizedType) readMethod.getGenericReturnType()).getActualTypeArguments()[0];
            loadBeanMetadata(root, typeClass);
            for (int ver : versions) {
                BeanMetadata beanMetadata = root.get(typeClass.getName()).get(ver);
                value = FieldFactory.create(field, propertyDescriptor, beanMetadata);
                multiVersionFields.get(ver).add(value);
            }
        } else {
            value = FieldFactory.create(field, propertyDescriptor);
            for (int ver : versions) {
                multiVersionFields.get(ver).add(value);
            }
        }
    }
}