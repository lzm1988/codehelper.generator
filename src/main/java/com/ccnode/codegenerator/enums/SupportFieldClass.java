package com.ccnode.codegenerator.enums;

/**
 * What always stop you is what you always believe.
 * <p>
 * Created by zhengjun.du on 2017/12/09 18:41
 */
public enum SupportFieldClass {
    STRING(0,"java.lang.String"),
    DATE(1,"java.util.Date"),
    BIG_DECIMAL(2,"java.math.BigDecimal"),
    INTEGER(3,"java.lang.Integer"),
    LONG(4,"java.lang.Long"),
    SHORT(5,"java.lang.Short"),
    DOUBLE(6,"java.lang.Double"),
    FLOAT(7,"java.lang.Float"),
    JAVA_SQL_TIMESTAMP(8,"java.sql.Timestamp"),
    JAVA_SQL_Date(9,"java.sql.Date"),
    BASIC_INT(10,"int"),
    BASIC_LONG(11,"long"),
    BASIC_SHORT(12,"short"),
    BASIC_DOUBLE(13,"double"),
    BASIC_FLOAT(14,"float"),
    NONE(-1,"none");

    private Integer code;
    private String desc;

    private SupportFieldClass(Integer code,String desc){
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static SupportFieldClass fromName(String name){
        for(SupportFieldClass e : SupportFieldClass.values()){
            if (e.name().equals(name)){
                return e;
            }
        }
        return SupportFieldClass.NONE;
    }

    public static SupportFieldClass fromCode(Integer code){
        for(SupportFieldClass e : SupportFieldClass.values()){
            if (e.getCode().equals(code)){
                return e;
            }
        }
        return SupportFieldClass.NONE;
    }

    public static SupportFieldClass fromDesc(String desc){
        for(SupportFieldClass e : SupportFieldClass.values()){
            if (e.getDesc().equalsIgnoreCase(desc)){
                return e;
            }
        }
        return SupportFieldClass.NONE;
    }


}
