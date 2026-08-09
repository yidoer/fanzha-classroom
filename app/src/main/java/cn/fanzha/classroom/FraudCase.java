package cn.fanzha.classroom;

import org.json.JSONArray;
import org.json.JSONObject;

public class FraudCase {
    public final String id;
    public final String title;
    public final String category;
    public final String risk;
    public final String materialType;
    public final String summary;
    public final String story;
    public final String warningSigns;
    public final String response;
    public final String sourceName;
    public final String sourceDate;
    public final String sourceUrl;
    public final boolean isScam;

    private FraudCase(JSONObject json) {
        id = json.optString("id");
        title = json.optString("title");
        category = json.optString("category");
        risk = json.optString("risk", "高风险");
        materialType = json.optString("materialType", "权威归纳");
        summary = json.optString("summary");
        story = json.optString("story");
        warningSigns = join(json.optJSONArray("warningSigns"));
        response = join(json.optJSONArray("response"));
        sourceName = json.optString("sourceName");
        sourceDate = json.optString("sourceDate");
        sourceUrl = json.optString("sourceUrl");
        isScam = json.optBoolean("isScam", true);
    }

    public static FraudCase fromJson(JSONObject json) {
        return new FraudCase(json);
    }

    public String searchableText() {
        return (title + category + materialType + summary + story + warningSigns + response + sourceName).toLowerCase();
    }

    public String publicTitle() {
        switch (id) {
            case "rebate-task": return "凌晨一点的兼职群";
            case "fake-investment": return "老师只带最后五个人";
            case "fake-shopping": return "橱窗里最后一件礼物";
            case "fake-loan": return "审批通过之后";
            case "fake-credit-repair": return "毕业多年的那笔账户";
            case "fake-customer-service": return "一通准确说出订单的电话";
            case "impersonate-leader": return "会议中的临时指令";
            case "fake-prosecutor": return "来自异地的紧急调查";
            case "dating-investment": return "海风那边的人";
            case "online-game": return "绝版账号的买家";
            case "fake-service": return "据说能解决问题的人";
            case "sextortion": return "深夜的新好友";
            case "pension": return "母亲参加的周末讲座";
            case "ai-impersonation": return "视频里的大学室友";
            case "cross-border-job": return "一份包机票的工作";
            case "overseas-call-center": return "海岛来电";
            case "smishing": return "积分清零前的十分钟";
            case "ticket": return "开场前四十八小时";
            case "charity": return "雨夜里的求助链接";
            case "receipt-code": return "少了一笔的晚班账单";
            case "deepfake-cfo": return "屏幕里的整个会议室";
            case "sim-swap": return "忽然消失的手机信号";
            case "crypto-romance": return "她说想和你计划未来";
            case "parcel-mule": return "客厅里堆起的快递箱";
            case "quishing": return "停车场里的新二维码";
            case "task-app-front-running": return "永不休息的交易程序";
            case "real-friend-help": return "老同学的凌晨消息";
            case "real-bank-alert": return "被你挂断的银行电话";
            case "real-seller": return "不肯降价的二手卖家";
            case "real-colleague": return "同事忘带的门禁卡";
            case "real-family-transfer": return "父亲第一次开口借钱";
            default: return "一次需要判断的相遇";
        }
    }

    public String publicTeaser() {
        switch (id) {
            case "real-friend-help": return "多年没联系的人突然出现。你记得旧情，也记得那些新闻。";
            case "real-bank-alert": return "陌生号码知道你的姓名和卡片尾号。接听还是挂断，都可能有代价。";
            case "real-seller": return "交易规则并不完美，对方的坚持究竟是可疑，还是有自己的理由？";
            case "real-colleague": return "一个不合规的小请求，夹在信任、制度和同事关系之间。";
            case "real-family-transfer": return "最亲近的人也可能说不清来龙去脉。核验会不会等于不信任？";
            default: return "信息看起来足够真实，但真正重要的细节还没有浮出水面。";
        }
    }

    public String publicShelf() {
        switch (id) {
            case "real-friend-help": case "real-family-transfer": case "ai-impersonation": case "dating-investment": case "crypto-romance": return "熟人之间";
            case "real-bank-alert": case "fake-customer-service": case "fake-credit-repair": case "fake-prosecutor": case "sim-swap": case "smishing": return "一通来电";
            case "real-seller": case "fake-shopping": case "online-game": case "ticket": case "quishing": case "receipt-code": return "一次交易";
            case "real-colleague": case "impersonate-leader": case "deepfake-cfo": case "cross-border-job": case "parcel-mule": return "工作现场";
            default: return "生活岔路";
        }
    }

    private static String join(JSONArray array) {
        if (array == null) return "";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            if (i > 0) text.append("\n");
            text.append("• ").append(array.optString(i));
        }
        return text.toString();
    }
}
