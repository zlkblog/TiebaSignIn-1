```java
package top.srcrs;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.srcrs.domain.Cookie;
import top.srcrs.util.Encryption;
import top.srcrs.util.Request;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 程序运行开始的地方
 * @author srcrs
 * @Time 2020-10-31
 */
public class Run
{
    /** 获取日志记录器对象 */
    private static final Logger LOGGER = LoggerFactory.getLogger(Run.class);

    /** 获取用户所有关注贴吧 - PC端JSON接口，支持分页 */
    String LIKE_URL = "https://tieba.baidu.com/f/like/mylike/json";
    /** 获取用户的tbs */
    String TBS_URL = "http://tieba.baidu.com/dc/common/tbs";
    /** 贴吧签到接口 */
    String SIGN_URL = "http://c.tieba.baidu.com/c/c/forum/sign";

    /** 存储用户所关注的贴吧 */
    private List<String> follow = new ArrayList<>();
    /** 签到成功的贴吧列表 */
    private static List<String> success = new ArrayList<>();
    /** 签到失败的贴吧列表 */
    private static List<String> failed = new ArrayList<>();
    /** 用户的tbs */
    private String tbs = "";
    /** 用户所关注的贴吧数量 */
    private static Integer followNum = 0;
    
    public static void main( String[] args ){
        Cookie cookie = Cookie.getInstance();
        // 存入Cookie，以备使用
        if(args.length==0){
            LOGGER.warn("请在Secrets中填写BDUSS");
        }
        cookie.setBDUSS(args[0]);
        Run run = new Run();
        run.getTbs();
        run.getFollow();
        run.runSign();
        
        // 输出签到结果汇总
        LOGGER.info("========================================");
        LOGGER.info("贴吧签到完成！共 {} 个贴吧", followNum);
        LOGGER.info("成功: {} 个", success.size());
        LOGGER.info("失败: {} 个", failed.size());
        
        // 输出失败的贴吧列表
        if(!failed.isEmpty()){
            LOGGER.warn("========== 失败贴吧列表 ==========");
            for(int i = 0; i < failed.size(); i++){
                LOGGER.warn("{}. {}", i + 1, failed.get(i));
            }
            LOGGER.warn("==================================");
        }
        
        // 输出成功的贴吧列表（简要）
        LOGGER.info("成功贴吧数: {}", success.size());
        LOGGER.info("========================================");
        
        if(args.length == 2){
            run.send(args[1], failed);
        }
    }

    /**
     * 进行登录，获得 tbs ，签到的时候需要用到这个参数
     * @author srcrs
     * @Time 2020-10-31
     */
    public void getTbs(){
        try{
            JSONObject jsonObject = Request.get(TBS_URL);
            if("1".equals(jsonObject.getString("is_login"))){
                LOGGER.info("获取tbs成功");
                tbs = jsonObject.getString("tbs");
            } else{
                LOGGER.warn("获取tbs失败 -- " + jsonObject);
            }
        } catch (Exception e){
            LOGGER.error("获取tbs部分出现错误 -- " + e);
        }
    }

    /**
     * 获取用户所关注的贴吧列表 - 优先PC端API支持分页，失败则用手机端API
     * @author srcrs
     * @Time 2020-10-31
     */
    public void getFollow(){
        // 先尝试PC端接口（支持分页，可以获取超过200个贴吧）
        boolean pcApiSuccess = false;
        try{
            int page = 1;
            int perPage = 50;
            boolean hasMore = true;
            
            while(hasMore){
                String pageUrl = LIKE_URL + "?pn=" + page + "&rn=" + perPage;
                JSONObject jsonObject = Request.get(pageUrl);
                LOGGER.info("PC端接口获取第 {} 页贴吧列表", page);
                
                // 检查返回是否为JSON（不是HTML错误页面）
                if(jsonObject == null || !jsonObject.containsKey("forum_list")){
                    LOGGER.warn("PC端接口返回异常，尝试手机端接口");
                    break;
                }
                
                JSONArray jsonArray = jsonObject.getJSONArray("forum_list");
                if(jsonArray == null || jsonArray.isEmpty()){
                    hasMore = false;
                    break;
                }
                
                for(Object array : jsonArray){
                    if("0".equals(((JSONObject) array).getString("is_sign"))){
                        follow.add(((JSONObject) array).getString("forum_name").replace("+","%2B"));
                    } else{
                        success.add(((JSONObject) array).getString("forum_name"));
                    }
                }
                
                if(jsonArray.size() < perPage){
                    hasMore = false;
                } else {
                    page++;
                    Thread.sleep(500);
                }
            }
            
            if(follow.size() > 0 || success.size() > 0){
                pcApiSuccess = true;
                followNum = follow.size() + success.size();
                LOGGER.info("PC端接口共获取 {} 个贴吧", followNum);
            }
            
        } catch (Exception e){
            LOGGER.warn("PC端接口获取失败 -- " + e);
        }
        
        // 如果PC端失败，使用手机端接口（支持分页）
        if(!pcApiSuccess){
            try{
                LOGGER.info("使用手机端接口获取（尝试分页）");
                int page = 1;
                int perPage = 200;
                boolean hasMore = true;
                
                while(hasMore){
                    String pageUrl = "https://tieba.baidu.com/mo/q/newmoindex?pn=" + page;
                    JSONObject jsonObject = Request.get(pageUrl);
                    JSONArray jsonArray = jsonObject.getJSONObject("data").getJSONArray("like_forum");
                    
                    if(jsonArray == null || jsonArray.isEmpty()){
                        hasMore = false;
                        break;
                    }
                    
                    LOGGER.info("手机端接口获取第 {} 页，获取到 {} 个贴吧", page, jsonArray.size());
                    
                    for (Object array : jsonArray) {
                        if("0".equals(((JSONObject) array).getString("is_sign"))){
                            follow.add(((JSONObject) array).getString("forum_name").replace("+","%2B"));
                        } else{
                            success.add(((JSONObject) array).getString("forum_name"));
                        }
                    }
                    
                    if(jsonArray.size() < perPage){
                        hasMore = false;
                    } else {
                        page++;
                        Thread.sleep(500);
                    }
                }
                
                followNum = follow.size() + success.size();
                LOGGER.info("手机端接口共获取 {} 个贴吧", followNum);
            } catch (Exception e2){
                LOGGER.error("手机端接口也失败 -- " + e2);
            }
        }
    }

    /**
     * 开始进行签到，每一轮性将所有未签到的贴吧进行签到，一共进行5轮，如果还未签到完就立即结束
     * @author srcrs
     * @Time 2020-10-31
     */
    public void runSign(){
        // 最多重试5轮
        Integer flag = 5;
        try{
            while(success.size()<followNum&&flag>0){
                LOGGER.info("------第 {} 轮签到-------", 5 - flag + 1);
                LOGGER.info("还剩 {} 个贴吧未签到", followNum - success.size());
                Iterator<String> iterator = follow.iterator();
                while(iterator.hasNext()){
                    String s = iterator.next();
                    String rotation = s.replace("%2B","+");
                    String body = "kw="+s+"&tbs="+tbs+"&sign="+ Encryption.enCodeMd5("kw="+rotation+"tbs="+tbs+"tiebaclient!!!");
                    JSONObject post = Request.post(SIGN_URL, body);
                    // 随机等待300-500ms，避免请求过快
                    int randomTime = new Random().nextInt(200) + 300;
                    LOGGER.info("等待 {} ms", randomTime);
                    TimeUnit.MILLISECONDS.sleep(randomTime);
                    if("0".equals(post.getString("error_code"))){
                        iterator.remove();
                        success.add(rotation);
                        LOGGER.info(rotation + ": " + "签到成功");
                    } else {
                        // 签到失败，记录到失败列表
                        LOGGER.warn(rotation + ": 签到失败 - " + post.getString("error_msg") + " (错误码:" + post.getString("error_code") + ")");
                        failed.add(rotation);
                    }
                }
                if(success.size() != followNum){
                    // 如果还有未签到的，等待5分钟继续下一轮
                    Thread.sleep(1000 * 60 * 5);
                    // 重新获取tbs
                    getTbs();
                }
                flag--;
            }
        } catch (Exception e){
            LOGGER.error("签到过程出现错误 -- " + e);
        }
    }

    /**
     * 发送签到结果到server酱推送
     * @param sckey server酱的sckey
     * @param failed 签到失败的贴吧列表
     * @author srcrs
     * @Time 2020-10-31
     */
    public void send(String sckey, List<String> failed){
        /** 发送签到结果到server酱 */
        String text = "贴吧签到 " + followNum + " - ";
        text += "成功 " + success.size() + " 失败:" + failed.size();
        String desp = "共 " + followNum + " 个贴吧\n";
        desp += "成功: " + success.size() + " 个\n";
        desp += "失败: " + failed.size() + " 个\n";
        
        // 添加失败贴吧列表
        if(!failed.isEmpty()){
            desp += "\n--- 失败贴吧列表 ---\n";
            for(int i = 0; i < failed.size(); i++){
                desp += (i + 1) + ". " + failed.get(i) + "\n";
            }
        }
        
        String body = "text="+text+"&desp="+"TiebaSignIn签到结果\n\n"+desp;
        StringEntity entityBody = new StringEntity(body,"UTF-8");
        HttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("https://sc.ftqq.com/"+sckey+".send");
        httpPost.addHeader("Content-Type","application/x-www-form-urlencoded");
        httpPost.setEntity(entityBody);
        HttpResponse resp = null;
        String respContent = null;
        try{
            resp = client.execute(httpPost);
            HttpEntity entity=null;
            if(resp.getStatusLine().getStatusCode()<400){
                entity = resp.getEntity();
            } else{
                entity = resp.getEntity();
            }
            respContent = EntityUtils.toString(entity, "UTF-8");
            LOGGER.info("server酱推送成功");
        } catch (Exception e){
            LOGGER.error("server酱推送失败 -- " + e);
        }
    }
}
```

直接复制全部替换 `src/main/java/top/srcrs/Run.java` 就行！
