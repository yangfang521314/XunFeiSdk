package com.lanfeng.young.xunfeisdk.speech;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.res.AssetManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iflytek.aiui.AIUIAgent;
import com.iflytek.aiui.AIUIConstant;
import com.iflytek.aiui.AIUIEvent;
import com.iflytek.aiui.AIUIListener;
import com.iflytek.aiui.AIUIMessage;
import com.lanfeng.young.xunfeisdk.ContactRepository;
import com.lanfeng.young.xunfeisdk.DynamicEntityData;
import com.lanfeng.young.xunfeisdk.RawMessage;
import com.lanfeng.young.xunfeisdk.SingleLiveEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by yf on 2018/8/9.
 */
public class AIUIRepository {
    private static final String TAG = AIUIRepository.class.getSimpleName();
    //交互状态
    private int mCurrentState = AIUIConstant.STATE_IDLE;
    private AIUIAgent mAIUIAgent = null;
    private Context context;
    //是否检测到前端点，提示 ’为说话‘ 时判断使用
    private boolean mVadBegin = false;
    private AIUIView mView;
    private ContactRepository contactRepository;
    private JSONObject mPersParams;
//    //vad事件
//    private MutableLiveData<AIUIEvent> mVADEvent = new MutableLiveData<>();
//    //唤醒和休眠事件
//    private MutableLiveData<AIUIEvent> mStateEvent = new SingleLiveEvent<>();
    private String phone_number;

    //处理PGS听写(流式听写）的队列
    private String voiceWords;
    private List<RawMessage> rawMessageList = new ArrayList<>();

    public AIUIRepository(Context mainActivity) {
        this.context = mainActivity;
        contactRepository = new ContactRepository(context);

    }

    private void initContract() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                List<String> contacts = contactRepository.getContacts();

                StringBuilder contactJson = new StringBuilder();
                for (String contact : contacts) {
                    String[] nameNumber = contact.split("\\$\\$");
                    contactJson.append(String.format("{\"name\": \"%s\", \"phoneNumber\": \"%s\" }\n",
                            nameNumber[0], nameNumber[1]));
                }
                syncDynamicData(new DynamicEntityData(
                        "IFLYTEK.telephone_contact", "uid", "", contactJson.toString()));
                putPersParam("uid", "");
            }
        }.start();
    }

    public void getContract() {
        initContract();

    }

    private void syncDynamicData(DynamicEntityData data) {
        Log.e(TAG, "syncDynamicData: " + mAIUIAgent);
        try {
            // 构造动态实体数据
            JSONObject syncSchemaJson = new JSONObject();
            JSONObject paramJson = new JSONObject();

            paramJson.put("id_name", data.idName);
            paramJson.put("id_value", data.idValue);
            paramJson.put("res_name", data.resName);

            syncSchemaJson.put("param", paramJson);
            syncSchemaJson.put("data", Base64.encodeToString(
                    data.syncData.getBytes(), Base64.DEFAULT | Base64.NO_WRAP));

            // 传入的数据一定要为utf-8编码
            byte[] syncData = syncSchemaJson.toString().getBytes("utf-8");

            AIUIMessage syncAthenaMessage = new AIUIMessage(AIUIConstant.CMD_SYNC,
                    AIUIConstant.SYNC_DATA_SCHEMA, 0, "", syncData);
//            sendMessage(syncAthenaMessage);
            mAIUIAgent.sendMessage(syncAthenaMessage);
        } catch (Exception e) {
            e.printStackTrace();
//            addMessageToDB(new RawMessage(AIUI, TEXT,
//                    String.format("上传动态实体数据出错 %s", e.getMessage()).getBytes()));
            Log.e(TAG, "syncDynamicData: 上传动态实体数据出错" + e.getMessage().getBytes());
        }
    }

    //生效动态实体
    public void putPersParam(String key, String value) {
        try {
            mPersParams = new JSONObject();
            mPersParams.put(key, value);
            setPersParams(mPersParams);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置个性化(动态实体和所见即可说)生效参数
     *
     * @param persParams
     */
    public void setPersParams(JSONObject persParams) {
        try {
            //参考文档动态实体生效使用一节
            JSONObject params = new JSONObject();
            JSONObject audioParams = new JSONObject();
            audioParams.put("pers_param", persParams.toString());
            params.put("audioparams", audioParams);

            sendMessage(new AIUIMessage(AIUIConstant.CMD_SET_PARAMS, 0, 0, params.toString(), null));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void startVoice() {
        mVadBegin = false;
        startRecordAudio();
    }

    private void startRecordAudio() {
        sendMessage(new AIUIMessage(AIUIConstant.CMD_START_RECORD, 0, 0, "data_type=audio,sample_rate=16000", null));
    }


    private void sendMessage(AIUIMessage message) {
        if (mAIUIAgent != null) {
            //确保AIUI处于唤醒状态
            if (mCurrentState != AIUIConstant.STATE_WORKING) {
                mAIUIAgent.sendMessage(new AIUIMessage(AIUIConstant.CMD_WAKEUP, 0, 0, "", null));
            }
            mAIUIAgent.sendMessage(message);
        }
    }


    /**
     * 读取配置
     */
    private String getAIUIParams() {
        String params = "";

        AssetManager assetManager = context.getResources().getAssets();
        try {
            InputStream ins = assetManager.open("cfg/aiui_phone.cfg");
            byte[] buffer = new byte[ins.available()];

            ins.read(buffer);
            ins.close();

            params = new String(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return params;
    }


    //AIUI事件监听器
    private AIUIListener mAIUIListener = new AIUIListener() {

        @Override
        public void onEvent(AIUIEvent event) {
            switch (event.eventType) {
                case AIUIConstant.EVENT_WAKEUP: {
//                    mStateEvent.postValue(event);

                }
                break;

                case AIUIConstant.EVENT_SLEEP: {
//                    mStateEvent.postValue(event);

                }
                break;

                case AIUIConstant.EVENT_STATE: {
                    mCurrentState = event.arg1;
                }
                break;

                case AIUIConstant.EVENT_RESULT: {
                    processResult(event);
                }
                break;

                case AIUIConstant.EVENT_TTS: {
//                    processTTS(aiuiEvent);
                }
                break;

                case AIUIConstant.EVENT_ERROR: {
                    //向消息列表中添加AIUI错误消息
                    Map<String, String> semantic = new HashMap<>();
                    semantic.put("errorInfo", event.info);

                }
                break;
                case AIUIConstant.EVENT_CMD_RETURN: {
//                    processCmdReturnEvent(aiuiEvent);
                }

                case AIUIConstant.EVENT_CONNECTED_TO_SERVER: {
//                    mUID.postValue(aiuiEvent.data.getString("uid"));
                }
                break;

                case AIUIConstant.EVENT_VAD: {
//                    mVADEvent.postValue(event);
                    if (AIUIConstant.VAD_BOS == event.arg1) {
                        mVadBegin = true;
                        //找到语音前端点
                    } else if (AIUIConstant.VAD_EOS == event.arg1) {
                        //找到语音后端点
                    } else {
                        Log.e(TAG, "onEvent: " + event.arg2);
                    }
                    if (AIUIConstant.VAD_VOL == event.arg1) {
                        mView.showVolume(event.arg2);
                    }
                }

                break;
            }
        }

    };

    /**
     * 处理AIUI结果事件（听写结果和语义结果）
     *
     * @param event 结果事件
     */
    private void processResult(AIUIEvent event) {
        try {
            JSONObject bizParamJson = new JSONObject(event.info);
            JSONObject data = bizParamJson.getJSONArray("data").getJSONObject(0);
            JSONObject params = data.getJSONObject("params");
            JSONObject content = data.getJSONArray("content").getJSONObject(0);

            long rspTime = event.data.getLong("eos_rslt", -1);  //响应时间
            if (content.has("cnt_id")) {
                String cnt_id = content.getString("cnt_id");
                JSONObject cntJson = new JSONObject(new String(event.data.getByteArray(cnt_id), "utf-8"));

                String sub = params.optString("sub");
                if ("nlp".equals(sub)) {
                    JSONObject semanticResult = cntJson.optJSONObject("intent");
                    if (semanticResult != null && semanticResult.length() != 0) {
                        //解析得到语义结果，将语义结果作为消息插入到消息列表中
                        Log.e(TAG, "processResult: " + semanticResult.toString());
                        getJsonString(semanticResult.toString());
                    }
                } else if ("iat".equals(sub)) {
                    //解析听写结果更新当前语音消息的听写内容
                    updateVoiceMessageFromIAT(cntJson);
                } else if ("itrans".equals(sub)) {
                    String sid = event.data.getString("sid", "");
                    updateMessageFromItrans(sid, params, cntJson, rspTime);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    /**
     * processResult:
     * {"rc":0,
     * "semantic":[{"intent":"INSTRUCTION","slots":[{"name":"insType","value":"CONFIRM"}]}],
     * "service":"telephone","uuid":"cida1144f9a@dx00db0ecde832010005",
     * "text":"是",
     * "state":{"fg::telephone::default::default":{"insType":"1","operation":"1","state":"default"}},
     * "used_state":{"insType":"1","operation":"1","state":"default","state_key":"fg::telephone::default::default"},
     * "dialog_stat":"dataInvalid","save_history":true,"sid":"cida1144f9a@dx00db0ecde832010005"}
     * <p>
     * RC O -> 成功
     * RC 1-> 成功
     * 2 ->  无效请求
     * 3 ->  服务器内部出错
     * 4 ->  服务器不理解或不能处理该文本
     *
     * @param semanticResult
     */
    private void getJsonString(String semanticResult) {
        //主要用于拨打电话的确定
        String value = null;
        //用于打开网址的url
        String normValue = null;
        //所需要的服务
        String service = null;
        //意图
        String intent = null;
        //语音识别返回的基本文本
        String text = null;
        //如天气返回的数据
        JsonObject result = null;
        JsonObject object = new JsonParser().parse(semanticResult).getAsJsonObject();
        if (object.has("service")) {
            service = object.get("service").getAsString();
        }
        if (("2").equals(object.get("rc").getAsString()) || ("4").equals(object.get("rc").getAsString())
                || ("3").equals(object.get("rc").getAsString())) {
            setListMessage("对不起主人，不能识别", service, null);
        } else {
            //基本回答的结果
            if (object.has("answer")) {
                JsonObject jsonObject = object.getAsJsonObject("answer");
                text = jsonObject.get("text").getAsString();
                Log.e(TAG, "getJsonString: " + text + "" + voiceWords);
            }
            //如天气回答的data数据
            if (object.has("data")) {
                result = object.getAsJsonObject("data");
            }
            if (!TextUtils.isEmpty(text)) {
                setListMessage(text, service, result);
            }
            //解析关键字段，semantic里面的如自定义动态数据网站url
            if (object.has("semantic")) {
                JsonArray data = object.get("semantic").getAsJsonArray();
                for (JsonElement jsonElement : data) {
                    JsonObject jsonObject = jsonElement.getAsJsonObject();
                    JsonArray slots = jsonObject.getAsJsonArray("slots");
                    intent = jsonObject.get("intent").getAsString();
                    for (int i = 0; i < slots.size(); i++) {
                        JsonObject object1 = slots.get(0).getAsJsonObject();
                        value = object1.get("value").getAsString();
                        if (object1.has("normValue")) {
                            normValue = object1.get("normValue").getAsString();
                        }
                    }
                }
                if (intent != null) {
                    if (intent.equals("DIAL")) {
                        //保存电话号码
                        if (value != null && !TextUtils.isEmpty(value)) {
                            phone_number = value;
                        }
                    }
                }
                Log.e(TAG, "getJsonString: "+intent+"service:"+service+"value:"+value );
                if ("telephone".equals(service) && "CONFIRM".equals(value) && "INSTRUCTION".equals(intent)) {
                    mView.showContent(service, phone_number);
                }

                //自定义字段，讯飞国搜客户端跳转网页
                if ("GUOSOU.open_web".equals(service) && "open_web".equals(intent)) {
                    mView.showContent(intent, normValue);
                }
                //定义搜索词，进入到国搜页面搜索
                if ("GUOSOU.chinaso_search".equals(service) && "chinaso_search".equals(intent)) {
                }
                //跳转App
                if ("app".equals(service) && "LAUNCH".equals(intent)) {
                    mView.showContent(intent, value);
                }

            }
        }

    }

    private void setListMessage(String text, String intent, JsonObject result) {
        RawMessage rawMessage = new RawMessage();
        rawMessage.setVoice(voiceWords);
        rawMessage.setMessage(text);
        rawMessage.setIntent(intent);
        rawMessage.setJsonObject(result);
        rawMessageList.add(rawMessage);
        mView.showText(rawMessageList);
    }

    private void updateVoiceMessageFromIAT(JSONObject cntJson) {
        Log.e(TAG, "updateVoiceMessageFromIAT: " + cntJson);
        JSONObject text = cntJson.optJSONObject("text");
        // 解析拼接此次听写结果
        StringBuilder iatText = new StringBuilder();
        JSONArray words = text.optJSONArray("ws");
        boolean lastResult = text.optBoolean("ls");
        for (int index = 0; index < words.length(); index++) {
            JSONArray charWord = words.optJSONObject(index).optJSONArray("cw");
            for (int cIndex = 0; cIndex < charWord.length(); cIndex++) {
                iatText.append(charWord.optJSONObject(cIndex).opt("w"));
            }
        }
        if (!TextUtils.isEmpty(iatText)) {
            voiceWords = iatText.toString();
        }

    }

    private void updateMessageFromItrans(String sid, JSONObject params, JSONObject cntJson, long rspTime) {
        String text = "";
        try {
            cntJson.put("sid", sid);

            JSONObject transResult = cntJson.optJSONObject("trans_result");
            if (transResult != null && transResult.length() != 0) {
                text = transResult.optString("dst");
            }

            if (TextUtils.isEmpty(text)) {
                return;
            }

            int rstId = params.optInt("rstid");
            if (rstId == 1) {
                JSONArray jsonArray = new JSONArray();
                jsonArray.put(cntJson);
//                anlaysize(jsonArray.toString().getBytes());
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }


    public void attach(AIUIView mView) {
        this.mView = mView;

    }

    public void detachView() {
        mView = null;
        stopRecordAudio();
    }

    public void stopAudio() {
        if (!mVadBegin) {
            mView.showErrorMessage("您好像么有说话");
        }
        sendMessage(new AIUIMessage(AIUIConstant.CMD_STOP_RECORD, 0, 0, "data_type=audio,sample_rate=16000", null));

    }

    private void stopRecordAudio() {
        if (null != mAIUIAgent) {
            sendMessage(new AIUIMessage(AIUIConstant.CMD_STOP, 0, 0, null, null));
            mAIUIAgent.destroy();
        }
    }

    public void initAIUIAgent() {
        if (null == mAIUIAgent) {
            Log.i(TAG, "create aiui agent");
            //创建AIUIAgent
            mAIUIAgent = AIUIAgent.createAgent(context, getAIUIParams(), mAIUIListener);
        }

        if (null == mAIUIAgent) {
            final String strErrorTip = "语音服务出错！";
            mView.showErrorMessage(strErrorTip);
        }
    }


    public void initWords() {
        RawMessage rawMessage = new RawMessage();
        rawMessage.setVoice(voiceWords);
        rawMessage.setMessage("你好，young");
        rawMessageList.add(rawMessage);
        mView.showText(rawMessageList);
    }
}
