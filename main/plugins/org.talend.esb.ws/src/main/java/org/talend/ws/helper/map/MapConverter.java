/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.talend.ws.helper.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author rlamarche
 */
public class MapConverter {

    public static final String SEPARATOR = ".";
    public static final String LEFT_SQUARE_BRACKET = "[";
    public static final String RIGHT_SQUARE_BRACKET = "]";

    public static Map<String, Object> deepMapToMap(Map<String, Object> map) {
        return deepMapToMap(map, null, SEPARATOR);
    }

    public static Map<String, Object> mapToDeepMap(Map<String, Object> map) {
        return mapToDeepMap(map, SEPARATOR);
    }

    private static Map<String, Object> deepMapToMap(Object value, String k, String sep) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, Object> out = new HashMap<String, Object>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (k == null) {
                    out.putAll(deepMapToMap(entry.getValue(), entry.getKey(), sep));
                } else {
                    out.putAll(deepMapToMap(entry.getValue(), k + sep + entry.
                            getKey(), sep));
                }
            }
            return out;
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            Map<String, Object> out = new HashMap<String, Object>();
            int i = 0;
            for (Object val : list) {
                StringBuffer sb = new StringBuffer();
                sb.append(k).append(LEFT_SQUARE_BRACKET).append(i).append(RIGHT_SQUARE_BRACKET);
                out.putAll(deepMapToMap(val, sb.toString(), sep));
                i++;
            }
            out.put(k + ".size", list.size());
            return out;
        } else {
            if (k == null) {
                throw new IllegalArgumentException("value must be a map or you must provide a key name");
            } else {
                Map<String, Object> out = new HashMap<String, Object>(1);
                out.put(k, value);
                return out;
            }
        }
    }

    private static Map<String, Object> mapToDeepMap(Map<String, Object> in, String sep) {
        Map<String, Object> out = new HashMap<String, Object>();
        Map<String, Map<String, Object>> stack = new HashMap<String, Map<String, Object>>();

        Map<String, List<Object>> listStack = new HashMap<String, List<Object>>();

        for (Map.Entry<String, Object> entry : in.entrySet()) {
            String key = entry.getKey();
            int pos = key.indexOf(sep);
            if (pos == -1) {
                int index = getIndexOfKey(key);
                if (index == -1) {
                    out.put(entry.getKey(), entry.getValue());
                } else {
                    String listName = key.substring(0, key.indexOf(LEFT_SQUARE_BRACKET));
                    List<Object> list = listStack.get(listName);
                    if (list == null) {
                        list = new ArrayList<Object>();
                        listStack.put(listName, list);
                    }
                    if (index < list.size()) {
                        list.set(index, entry.getValue());
                    } else {
                        for (int i = list.size(); i < index; i++) {
                            list.add(null);
                        }
                        list.add(entry.getValue());
                    }
                }
            } else {
                String k = key.substring(0, pos);
                int index = getIndexOfKey(k);
                String subK = key.substring(pos + sep.length());
                if (index == -1) {
                    Map<String, Object> stackVal = stack.get(k);
                    if (stackVal == null) {
                        stackVal = new HashMap<String, Object>();
                        stack.put(k, stackVal);
                    }
                    stackVal.put(subK, entry.getValue());
                } else {
                    String listName = k.substring(0, k.indexOf(LEFT_SQUARE_BRACKET));
                    List<Object> list = listStack.get(listName);
                    if (list == null) {
                        list = new ArrayList<Object>();
                        listStack.put(listName, list);
                    }
                    Map<String, Object> lmap = null;
                    if (index < list.size()) {
                        lmap = (Map<String, Object>) list.get(index);
                        if (lmap == null) {
                            lmap = new HashMap<String, Object>();
                            list.set(index, lmap);
                        }
                    } else {
                        for (int i = list.size(); i < index; i++) {
                            list.add(null);
                        }
                        list.add(new HashMap<String, Object>());
                        lmap = (Map<String, Object>) list.get(index);
                    }

                    lmap.put(subK, entry.getValue());
                }
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : stack.entrySet()) {
            out.put(entry.getKey(), mapToDeepMap(entry.getValue(), sep));
        }
        for (Map.Entry<String, List<Object>> entry : listStack.entrySet()) {
            List<Object> values = new ArrayList<Object>(entry.getValue().size());
            for (Object val : entry.getValue()) {
                if (val instanceof Map) {
                    values.add(mapToDeepMap((Map<String, Object>) val, sep));
                } else {
                    values.add(val);
                }
            }

            out.put(entry.getKey(), values);
        }

        return out;
    }

    private static int getIndexOfKey(String key) {
        int lpos = key.indexOf(LEFT_SQUARE_BRACKET);
        if (lpos == -1) {
            return -1;
        } else {
            int rpos = key.indexOf(RIGHT_SQUARE_BRACKET);
            if (rpos != key.length() - 1) {
                return -1;
            } else {
                if (rpos - lpos > 1) {
                    String strVal = key.substring(lpos + 1, rpos);

                    try {
                        return Integer.valueOf(strVal);
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                } else {
                    return -1;
                }
            }
        }
    }
}