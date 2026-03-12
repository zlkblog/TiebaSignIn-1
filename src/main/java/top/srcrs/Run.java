package top.srcrs;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.srcrs.domain.Cookie;
import top.srcrs.util.Encryption;
import top.srcrs.util.Request;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 程序运行开始的地方
 *
 * @author srcrs
 * @Time 2020-10-31
 */
public class Run {
    private static final Logger LOGGER = LoggerFactory.getLogger(Run.class);

    String LIKE_URL = "https://tieba.baidu.com/mo/q/newmoindex";
    String TBS_URL = "http://tieba.baidu.com/dc/common/tbs";
    String SIGN_URL = "http://c.tieba.baidu.com/c/c/forum/sign";

    private List<String> follow = new ArrayList<>();
    private static List<String> success = new ArrayList<>();
    private static HashSet<String> failed = new HashSet<String>();
    private static List<String> invalid = new ArrayList<>();
    private String tbs = "";
    private static Integer followNum = 1001;

    public static void main(String[] args) {
        Cookie cookie = Cookie.getInstance();
        if (args.length == 0) {
            LOGGER.warn("请在Secrets中填写BDUSS");
        }
        cookie.setBDUSS(args[0]);
        Run run = new Run();
        run.getTbs();
        run.getFollow();
        run.runSign();
        LOGGER.info("共 {} 个贴吧 - 成功: {} - 失败: {} - {} ", followNum, success.size(), followNum - success.size(), failed);
        LOGGER.info("失效 {} 个贴吧: {} ", invalid.size(), invalid);
        if (args.length == 2) {
            run.send(args[1]);
        }
    }

    public void getTbs() {
        try {
            JSONObject jsonObject = Request.get(TBS_URL);
            if ("1".equals(jsonObject.getString("is_login"))) {
                LOGGER.info("获取tbs成功");
                tbs = jsonObject.getString("tbs");
            } else {
                LOGGER.warn("获取tbs失败 -- " + jsonObject);
            }
        } catch (Exception e) {
            LOGGER.error("获取tbs部分出现错误 -- " + e);
        }
    }

    public void getFollow() {
        try {
            JSONObject jsonObject = Request.get(LIKE_URL);
            LOGGER.info("获取贴吧列表成功");
            JSONArray jsonArray = jsonObject.getJSONObject("data").getJSONArray("like_forum");
            followNum = jsonArray.size();
            for (Object array : jsonArray) {
                String tiebaName = ((JSONObject) array).getString("forum_name");
                if ("0".equals(((JSONObject) array).getString("is_sign"))) {
                    follow.add(tiebaName.replace("+", "%2B"));
                    if (Request.isTiebaNotExist(tiebaName)) {
                        follow.remove(tiebaName);
                        invalid.add(tiebaName);
                        failed.add(tiebaName);
                    }
                } else {
                    success.add(tiebaName);
                }
            }
        } catch (Exception e) {
            LOGGER.error("获取贴吧列表部分出现错误 -- " + e);
        }
    }

    public void runSign() {
        Integer flag = 5;
        try {
            while (success.size() < followNum && flag > 0) {
                LOGGER.info("-----第 {} 轮签到开始-----", 5 - flag + 1);
                LOGGER.info("还剩 {} 贴吧需要签到", followNum - success.size());
                Iterator<String> iterator = follow.iterator();
                while (iterator.hasNext()) {
                    String s = iterator.next();
                    String rotation = s.replace("%2B", "+");
                    String body = "kw=" + s + "&tbs=" + tbs + "&sign=" + Encryption.enCodeMd5("kw=" + rotation + "tbs=" + tbs + "tiebaclient!!!");
                    JSONObject post = Request.post(SIGN_URL, body);
                    int randomTime = new Random().nextInt(200) + 300;
                    LOGGER.info("等待 {} 毫秒", randomTime);
                    TimeUnit.MILLISECONDS.sleep(randomTime);
                    if ("0".equals(post.getString("error_code"))) {
                        iterator.remove();
                        success.add(rotation);
                        failed.remove(rotation);
                        LOGGER.info(rotation + ": " + "签到成功");
                    } else {
                        failed.add(rotation);
                        LOGGER.warn(rotation + ": " + "签到失败");
                    }
                }
                if (success.size() != followNum - invalid.size()) {
                    Thread.sleep(1000 * 60 * 5);
                    getTbs();
                }
                flag--;
            }
        } catch (Exception e) {
            LOGGER.error("签到部分出现错误 -- " + e);
        }
    }

    /**
     * 发送运行结果到微信，通过 Server酱（sct.ftqq.com）
     */
    public void send(String sckey) {
        try {
            String text = URLEncoder.encode("百度贴吧自动签到结果", "UTF-8");
            String desp = "";
            desp += "✨ 签到结果\n";
            desp += "📊 总数: " + followNum + "\n";
            desp += "✅ 成功: " + success.size() + "\n";
            desp += "❌ 失败: " + failed.size() + "\n";
            desp += "⚠️ 失效: " + invalid.size() + "\n\n";
            if (failed.size() > 0) {
                desp += "💥 失败的贴吧:\n";
                for (String tieba : failed) {
                    desp += "- " + tieba + "\n";
                }
            }
            desp = URLEncoder.encode(desp, "UTF-8");
            
            String url = "https://sct.ftqq.com/" + sckey + ".send?text=" + text + "&desp=" + desp;
            URL serverUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) serverUrl.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            LOGGER.info("Server酱推送结果: " + response.toString());
            connection.disconnect();

        } catch (Exception e) {
            LOGGER.error("Server酱发送失败 -- " + e);
        }
    }
}
