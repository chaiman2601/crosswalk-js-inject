package com.chenyg.crossjs;


import android.text.TextUtils;
import android.util.Log;
import com.google.gson.Gson;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Iterator;

class JsCallJava
{
    private final static String TAG = "JsCallJava";
    private final static String RETURN_RESULT_FORMAT = "{\"code\": %d, \"result\": %s}";

    private HashMap<String, MethodClass> methodsMap;

    private String preloadInterfaceJS;
    private Gson gson;
    private boolean willPrintDebugInfo, searchMoreForObjFun;
    private String injectTml;
    String topName;


    static class MethodClass
    {
        Method method;
        Object object;

        public MethodClass(Method method, Object object)
        {
            this.method = method;
            this.object = object;
        }
    }


    public JsCallJava(String topName, boolean willPrintDebugInfo, InjectObj... injectObjs)
    {
        try
        {
            this.topName = topName;
            this.searchMoreForObjFun = true;
            this.willPrintDebugInfo = willPrintDebugInfo;
            methodsMap = new HashMap<>();

            //final String testNamespace = "android_cross_js";

            String tmlCommon = WEBUtil.loadPackageJs(getClass(), "/crossjs/js-java-common.js", willPrintDebugInfo);
            String tml = WEBUtil.loadPackageJs(getClass(), "/crossjs/js-java.js", willPrintDebugInfo);
            this.injectTml = tml;

            String extension = WEBUtil.loadPackageJs(getClass(), "/crossjs/crossjs-extension.js", willPrintDebugInfo);

            extension = extension.replace("<LOG>", String.valueOf(willPrintDebugInfo));

            String extensionDynamic = WEBUtil
                    .loadPackageJs(getClass(), "/crossjs/crossjs-extension-dynamic.js", willPrintDebugInfo);

            StringBuilder sbuilder = new StringBuilder();

            sbuilder.append(extension);

            injectOne(sbuilder, new InjectObj(topName), tmlCommon, true, topName);

            for (InjectObj injectObj : injectObjs)
            {
                injectOne(sbuilder, injectObj, tml, false, topName);
            }
            sbuilder.append(extensionDynamic);

            preloadInterfaceJS = sbuilder.toString();
            if (willPrintDebugInfo)
            {
                Log.d(TAG, "the whole js:length=" + preloadInterfaceJS.length());
                Log.d(TAG, preloadInterfaceJS);
            }
        } catch (Exception e)
        {
            e.printStackTrace();
            throw new RuntimeException("init js error:" + e.getMessage());
        }
    }

    public boolean willPrintDebugInfo()
    {
        return willPrintDebugInfo;
    }


    /**
     * 用于动态注入。
     *
     * @param injectObj
     * @return
     * @throws Exception
     */
    String dynamicInjectOne(InjectObj injectObj) throws Exception
    {
        StringBuilder stringBuilder = new StringBuilder();
        injectOne(stringBuilder, injectObj, injectTml, false, topName);
        return stringBuilder.toString();
    }

    private void injectOne(StringBuilder sbuilder, InjectObj injectObj, String tml, boolean isCommon,
            String topName) throws Exception
    {


        StringBuilder smethods = new StringBuilder();


        for (int i = 0; i < injectObj.interfaceClasses.size(); i++)
        {
            Class<?> c = injectObj.interfaceClasses.get(i);
            Constructor constructor = c.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object obj = constructor.newInstance();
            searchClassMethods(injectObj.namespace, smethods, obj);
        }


        for (int i = 0; i < injectObj.interfaceObjects.size(); i++)
        {
            Object object = injectObj.interfaceObjects.get(i);
            searchClassMethods(injectObj.namespace, smethods, object);
        }


        StringBuilder namespaces = new StringBuilder();

        if (!isCommon)
        {
            StringBuilder temp = new StringBuilder();
            String[] ss = injectObj.namespace.split("\\.");
            for (String s : ss)
            {
                if ("".equals(s))
                {
                    continue;
                } else
                {
                    temp.append(".").append(s);
                    namespaces.append("global").append(temp).append("=").append("global").append(temp).append("||{};");
                }
            }
        }


        tml = tml.replace("<LOG>", String.valueOf(willPrintDebugInfo));
        tml = tml.replace("<GLOBAL>", "exports");

        if (isCommon)
        {
            tml = tml.replace("<BRIDGE>", "__bridge__");
            tml = tml.replace("<RETURN_BRIDGE>", "__async_bridge__");
            tml = tml.replace("<SEARCH_MORE>", String.valueOf(searchMoreForObjFun));

            tml = tml.replace("<JS_FUNCTION_STARTS>", InjectObj.JS_FUNCTION_STARTS);
            tml = tml.replace("<JAVA_CALLBACK>", "\"" + JavaFunction.JAVA_CALLBACK + "\"");
            tml = tml.replace("<TOP_NAME>", "common");
            tml = tml.replace("<COMMON_NAMESPACES>", namespaces);
        } else
        {
            tml = tml.replace("<TOP_NAME>", topName);
            tml = tml.replace("<NAMESPACE>", "\"" + injectObj.namespace + "\"");
            tml = tml.replace("<INJECT_NAME>", injectObj.namespace);
            tml = tml.replace("<INJECT_HANDLE>", "__injectHandle__");
            tml = tml.replace("<HOST_APP>", injectObj.namespace);
            tml = tml.replace("<HOST_APP_NAMESPACES>", namespaces);
            tml = tml.replace("<HOST_APP_FUN>", smethods);
        }


        if (willPrintDebugInfo)
        {
            Log.d(TAG, "<***************************");
            Log.d(TAG, namespaces.toString());
            Log.d(TAG, smethods.toString());
            Log.d(TAG, "***************************>");
        }

        sbuilder.append(tml);
    }

    private void searchClassMethods(String namespace, StringBuilder smethods, Object interfaceObj) throws Exception
    {
        /////个人建议还是用getMethods,这样可以不用把所有的函数都挤在一个类里，而可以把一部分放在父类中.//////
        Method[] methods = interfaceObj.getClass().getMethods();

        for (Method method : methods)
        {
            String sign;

            if ((sign = genJavaMethodSign(
                    method, true, JsInterface.class)) == null)
            {
                continue;
            }
            if (willPrintDebugInfo())
            {
                Log.d(TAG, "method:" + namespace + "." + sign);

            }
            methodsMap.put(namespace + "." + sign, new MethodClass(method, interfaceObj));
            smethods.append("global.").append(namespace).append('.').append(method.getName()).append('=');
        }
    }


    private Object[] dealParamsFirst(JsInterface jsInterface, Object[] values, int instanceId)
    {
        JsInterface.Param[] params = jsInterface.paramsFirst();
        if (params.length == 0)
        {
            return values;
        }
        Object[] vs = new Object[values.length + params.length];
        System.arraycopy(values, 0, vs, params.length, values.length);
        for (int i = 0; i < params.length; i++)
        {
            switch (params[i])
            {

                case builder:
                {
                    vs[i] = new JsInterface.Builder(instanceId, willPrintDebugInfo);
                }
                break;
            }
        }
        return vs;
    }

    private static boolean dealSignOfParamsFirst(Class<?> type, JsInterface jsInterface,
            int index) throws RuntimeException
    {
        boolean willContinue = false;
        if (index < jsInterface.paramsFirst().length)
        {

            switch (jsInterface.paramsFirst()[index])
            {

                case builder:
                {
                    if (!type.equals(JsInterface.Builder.class))
                    {
                        throw new RuntimeException(
                                "The param at index of " + index + " have to be the type of " + JsInterface.Builder
                                        .class);
                    }
                    willContinue = true;
                }
                break;
            }

        }
        return willContinue;
    }

    static String genJavaMethodSign(Method method, boolean needMethodName, Class<JsInterface> annotaion)
    {
        if (Modifier
                .isStatic(method.getModifiers()) || !method.isAnnotationPresent(annotaion))
        {
            return null;
        }

        String sign = needMethodName ? method.getName() : "";
        Class[] argsTypes = method.getParameterTypes();
        int len = argsTypes.length;
        JsInterface jsInterface = method.getAnnotation(annotaion);
        if (argsTypes.length < jsInterface.paramsFirst().length)
        {
            throw new RuntimeException(
                    "The param list length of " + method + " is less than the length of " + JsInterface.class
                            .getSimpleName() + " paramsFirst.");
        }
        for (int k = 0; k < len; k++)
        {
            Class cls = argsTypes[k];
            if (dealSignOfParamsFirst(cls, jsInterface, k))
            {
                continue;
            } else if (cls == String.class)
            {
                sign += "_S";
            } else if (cls == int.class ||
                    cls == long.class ||
                    cls == float.class ||
                    cls == double.class)
            {
                sign += "_N";
            } else if (cls == boolean.class)
            {
                sign += "_B";
            } else if (cls == JSONObject.class)
            {
                sign += "_O";
            } else if (cls == JSONArray.class)
            {
                sign += "_A";
            } else if (cls == JsFunction.class)
            {
                sign += "_F";
            } else
            {
                sign += "_P";
            }
        }
        return sign;
    }

    public String getPreloadInterfaceJS()
    {
        return preloadInterfaceJS;
    }


    private Object parseObj(WebBridge webBridge, String namespace, Object obj, int instanceId) throws JSONException
    {
        if (obj != null)
        {
            if ((obj instanceof String) && ((String) obj).startsWith(InjectObj.JS_FUNCTION_STARTS))
            {
                JsFunction jsFunction =
                        new JsFunction(webBridge, topName, namespace, (String) obj, instanceId, willPrintDebugInfo());
                obj = jsFunction;
            } else if ((obj instanceof JSONObject) && searchMoreForObjFun)
            {
                parseJSON(webBridge, namespace, (JSONObject) obj, instanceId);
            } else if ((obj instanceof JSONArray) && searchMoreForObjFun)
            {
                JSONArray array = (JSONArray) obj;
                for (int i = 0; i < array.length(); i++)
                {
                    Object object = array.get(i);
                    if (object != null)
                        array.put(i, parseObj(webBridge, namespace, object, instanceId));
                }
            }
        }
        return obj;
    }

    private void parseJSONArray(WebBridge webBridge, String namespace, JSONArray array,
            int instanceId) throws JSONException
    {
        for (int i = 0; i < array.length(); i++)
        {
            array.put(i, parseObj(webBridge, namespace, array.get(i), instanceId));
        }
    }

    private void parseJSON(WebBridge webBridge, String namespace, JSONObject json, int instanceId) throws JSONException
    {
        Iterator<String> names = json.keys();
        while (names.hasNext())
        {
            String name = names.next();
            Object obj = json.get(name);
            json.put(name, parseObj(webBridge, namespace, obj, instanceId));
        }
    }

    public String call(WebBridge webBridge, int instanceId, String jsonStr)
    {
        if (TextUtils.isEmpty(jsonStr))
        {
            return getReturn(jsonStr, null, 500, "call data empty");
        }
        try
        {
            JSONObject callJson = new JSONObject(jsonStr);

            if (willPrintDebugInfo)
            {
                Log.w(TAG, jsonStr);
            }

            String namespace = callJson.getString("namespace");
            boolean isJavaCallback = callJson.getBoolean("isJavaCallback");

            String methodName = callJson.getString("method");
            JSONArray argsTypes = callJson.getJSONArray("types");
            JSONArray argsVals = callJson.getJSONArray("args");

            Object[] values;
            DealArgsTemp dealArgsTemp;
            MethodClass currMethod;
            if (isJavaCallback)
            {
                String callbackId = argsVals.getString(0);
                String javaCallbackType = argsVals.getString(1);
                int startIndex = 2;

                if ("destroy".equals(javaCallbackType))
                {
                    JavaFunction.remove(callbackId, instanceId);
                    return getReturn(JavaFunction.JAVA_CALLBACK, methodName, 200, null);
                } else
                {
                    JavaFunction javaFunction = JavaFunction.get(callbackId, instanceId);
                    if (javaFunction == null)
                    {
                        return getReturn(JavaFunction.JAVA_CALLBACK, methodName, 500,
                                "not found javaFunction(id=" + callbackId + ")");
                    }
                    switch (javaCallbackType)
                    {
                        case "setPermanent":
                        {
                            boolean isPermanent = argsVals.getBoolean(startIndex);
                            javaFunction.setPermanent(isPermanent);
                            return getReturn(JavaFunction.JAVA_CALLBACK, methodName, 200, null);
                        }

                        case "callback":
                        {
                            values = new Object[argsVals.length() - startIndex];
                            dealArgsTemp = dealArgs(webBridge, values, 0, "", argsTypes, argsVals, startIndex,
                                    namespace, instanceId);
                            currMethod = javaFunction.getMethodClass(dealArgsTemp.sign);
                            namespace = "";
                        }
                        break;
                        default:
                            return getReturn(JavaFunction.JAVA_CALLBACK, methodName, 500,
                                    "unknown javaCallbackType(" + javaCallbackType + ")");
                    }

                }
            } else
            {
                values = new Object[argsTypes.length()];
                dealArgsTemp = dealArgs(webBridge, values, 0, methodName, argsTypes, argsVals, 0, namespace,
                        instanceId);
                currMethod = methodsMap.get(namespace + "." + dealArgsTemp.sign);
            }

            if (currMethod != null)
            {
                JsInterface jsInterface = currMethod.method.getAnnotation(JsInterface.class);
                values = dealParamsFirst(jsInterface, values, instanceId);

            }

            String sign = dealArgsTemp.sign;
            int numIndex = dealArgsTemp.numIndex;


            // 方法匹配失败
            if (currMethod == null)
            {
                return getReturn(jsonStr, null, 500,
                        "not found method(" + (TextUtils
                                .isEmpty(namespace) ? "" : namespace + ".") + sign + ") with valid parameters");
            }

            dealNum(numIndex, currMethod, values, argsVals);

            String rs = getReturn(jsonStr, (TextUtils.isEmpty(namespace) ? "" : namespace + ".") + sign, 200,
                    currMethod.method.invoke(currMethod.object, values));
            return rs;
        } catch (Exception e)
        {
            if (willPrintDebugInfo)
            {
                e.printStackTrace();
            }
            //优先返回详细的错误信息
            if (e.getCause() != null)
            {
                return getReturn(jsonStr, null, 500, "method execute error:" + e.getCause().getMessage());
            }
            return getReturn(jsonStr, null, 500, "method execute error:" + e.getMessage());
        }

    }


    private void dealNum(int numIndex, MethodClass currMethod, Object[] values, JSONArray argsVals) throws JSONException
    {
        // 数字类型细分匹配
        if (numIndex > 0)
        {
            Class[] methodTypes = currMethod.method.getParameterTypes();
            int currIndex;
            Class currCls;
            while (numIndex > 0)
            {
                currIndex = numIndex - numIndex / 10 * 10;
                currCls = methodTypes[currIndex];
                if (currCls == int.class)
                {
                    values[currIndex] = argsVals.getInt(currIndex - 1);
                } else if (currCls == long.class)
                {
                    //WARN: argsJson.getLong(k + defValue) will return a bigger incorrect number
                    values[currIndex] = Long.parseLong(argsVals.getString(currIndex - 1));
                } else
                {
                    values[currIndex] = argsVals.getDouble(currIndex - 1);
                }
                numIndex /= 10;
            }
        }
    }

    static class DealArgsTemp
    {
        String sign;
        int numIndex = 0;

        public DealArgsTemp(String sign, int numIndex)
        {
            this.sign = sign;
            this.numIndex = numIndex;
        }
    }

    /**
     * 把js端传来的参数列表进行转换。
     *
     * @return sign
     * @throws JSONException
     */
    private DealArgsTemp dealArgs(WebBridge webBridge, Object[] values, int offset, String methodName,
            JSONArray argsTypes,
            JSONArray argsVals, int offsetArgs, String namespace, int instanceId) throws JSONException

    {
        String sign = methodName;
        int numIndex = 0;
        String currType;
        int len = argsTypes.length();
        for (int m = offsetArgs; m < len; m++, offset++)
        {
            currType = argsTypes.optString(m);
            if ("string".equals(currType))
            {
                sign += "_S";
                values[offset] = argsVals.isNull(m) ? null : argsVals.getString(m);
            } else if ("number".equals(currType))
            {
                sign += "_N";
                numIndex = numIndex * 10 + m + 1;
            } else if ("boolean".equals(currType))
            {
                sign += "_B";
                values[offset] = argsVals.getBoolean(m);
            } else if ("object".equals(currType))
            {

                Object obj = argsVals.get(m);
                if (!argsVals.isNull(m))
                {
                    if (obj instanceof JSONArray)
                    {
                        sign += "_A";
                        parseJSONArray(webBridge, namespace, (JSONArray) obj, instanceId);
                    } else if (obj instanceof JSONObject)
                    {
                        sign += "_O";
                        parseJSON(webBridge, namespace, (JSONObject) obj, instanceId);
                    }
                } else
                {
                    sign += "_O";
                }
                values[offset] = obj;
            } else if ("function".equals(currType))
            {
                sign += "_F";
                JsFunction jsFunction = new JsFunction(webBridge, topName, namespace, argsVals.getString(m), instanceId,
                        willPrintDebugInfo());
                values[offset] = jsFunction;
            } else
            {
                sign += "_P";
            }
        }

        if (willPrintDebugInfo)
        {
            Log.d(TAG, "sign=" + sign + ",method=" + methodName);
        }

        return new DealArgsTemp(sign, numIndex);

    }

    private String getReturn(String reqJson, String callName, int stateCode, Object result)
    {
        if (willPrintDebugInfo())
        {
            Log.d(TAG, "method return:" + result);
        }
        String insertRes;
        if (result == null)
        {
            insertRes = "null";
        } else if (result instanceof String)
        {
            result = (((String) result).replace("\"", "\\\"")).replace("\n", "\\n").replace("\r", "\\r");
            insertRes = "\"" + result + "\"";
        } else if (result instanceof JavaFunction)
        {
            insertRes = "\"" + result + "\"";
        } else if (!(result instanceof Integer)
                && !(result instanceof Long)
                && !(result instanceof Boolean)
                && !(result instanceof Float)
                && !(result instanceof Double)
                && !(result instanceof JSONObject)
                && !(result instanceof JSONArray))
        {    // 非数字或者非字符串的构造对象类型都要序列化后再拼接
            if (gson == null)
            {
                gson = new Gson();
            }
            insertRes = gson.toJson(result);
        } else
        {  //直接转化
            insertRes = String.valueOf(result);
        }

        String resStr = String.format(RETURN_RESULT_FORMAT, stateCode, insertRes);
        if (willPrintDebugInfo)
        {
            Log.d(TAG, "result:" + resStr);
        }
        ////////
        return resStr;
    }
}
