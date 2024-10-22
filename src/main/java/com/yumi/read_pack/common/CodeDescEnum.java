package com.yumi.read_pack.common;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface CodeDescEnum <T extends Enum<T> & CodeDescEnum<T>> {
    /**序列化*/
    default Integer getCode() {return State.codeDescMap.get(self()).getCode();}
    default String getDesc() {return State.codeDescMap.get(self()).getDesc();}
    T self();
    default void addCodeDesc(int code, String desc) {
        T self = self();
        State.codeDescMap.putIfAbsent(self, new CodeDesc(code, desc));
        Map<Integer, CodeDescEnum<?>> codeDescEnums = State.code2EnumMap.computeIfAbsent(self.getClass(),
                key -> new HashMap<>());
        codeDescEnums.put(code, self);
    }

    /**反序列化*/
    @SuppressWarnings("unchecked")
    static <T extends Enum<T> & CodeDescEnum<T>> T parseFromCode(final Class<T> clazz, int code) {
        Map<Integer, CodeDescEnum<?>> integerCodeDescEnumMap = State.code2EnumMap.get(clazz);
        if (null == integerCodeDescEnumMap) {
            synchronized (clazz) {
                integerCodeDescEnumMap = State.code2EnumMap.get(clazz);
                if(null == integerCodeDescEnumMap) {
                    //保证初始化
                    try {
                        clazz.getMethod("values").invoke(null);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        assert integerCodeDescEnumMap != null;
        return (T) integerCodeDescEnumMap.get(code);
    }

}

class CodeDesc {
    private final Integer code;
    private final String desc;

    public CodeDesc(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}

class State {
    static ConcurrentHashMap<CodeDescEnum<?>, CodeDesc> codeDescMap = new ConcurrentHashMap<>();
    static ConcurrentHashMap<Class<?>, Map<Integer, CodeDescEnum<?>>> code2EnumMap = new ConcurrentHashMap<>();
}
