package org.yzh.framework.orm;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yzh.framework.orm.annotation.Field;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;

/**
 * 消息定义
 * @author yezhihao
 * @home https://gitee.com/yezhihao/jt808-server
 */
public abstract class BasicField<T> implements Comparable<BasicField> {
    protected static Logger log = LoggerFactory.getLogger(BasicField.class.getSimpleName());

    protected final int index;
    protected final int length;
    protected final String desc;
    protected final Method readMethod;
    protected final Method writeMethod;
    protected final PropertyDescriptor property;
    protected final Field field;

    public BasicField(Field field, PropertyDescriptor property) {
        this.index = field.index();
        int length = field.length();
        if (length < 0)
            length = field.type().length;
        this.length = length;
        this.desc = field.desc();
        this.readMethod = property.getReadMethod();
        this.writeMethod = property.getWriteMethod();
        this.field = field;
        this.property = property;
    }

    public abstract T readValue(ByteBuf buf, int length);

    public abstract void writeValue(ByteBuf buf, T value);

    public boolean readTo(ByteBuf buf, Object target) throws Exception {
        if (!buf.isReadable(length))
            return false;
        Object value = readValue(buf, length);
        writeMethod.invoke(target, value);
        return true;
    }

    public void writeTo(Object source, ByteBuf buf) throws Exception {
        Object value = readMethod.invoke(source);
        if (value != null)
            writeValue(buf, (T) value);
    }

    public void println(ByteBuf buf, int before, int after, Object value) {
        String hex = ByteBufUtil.hexDump(buf.slice(before, after - before));
        if (value == null)
            System.out.println(index + "\t" + "[" + hex + "] " + desc + ": null");
        else
            System.out.println(index + "\t" + "[" + hex + "] " + desc + ": " + (value.getClass().isArray() ? ArrayUtils.toString(value) : value));
    }

    @Override
    public int compareTo(BasicField that) {
        return Integer.compare(this.index, that.index);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(60);
        sb.append('{');
        sb.append("index=").append(index);
        sb.append(", length=").append(length);
        sb.append(", desc").append(desc);
        sb.append(", readMethod=").append(readMethod.getName());
        sb.append(", writeMethod=").append(writeMethod.getName());
        sb.append('}');
        return sb.toString();
    }
}