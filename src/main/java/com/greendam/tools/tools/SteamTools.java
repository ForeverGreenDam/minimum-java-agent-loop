package com.greendam.tools.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greendam.config.ConfigLoader;
import com.greendam.tools.annotation.Param;
import com.greendam.tools.annotation.Tool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Steam 工具集 — 提供 Steam 平台数据查询能力.
 *
 * <p>功能包括：
 * <ul>
 *   <li>查询用户最近两周游戏时长</li>
 *   <li>查询用户拥有的游戏列表及时长统计</li>
 *   <li>查询用户在指定游戏中的成就完成情况</li>
 *   <li>查询玩家基本资料</li>
 * </ul>
 *
 * <p>需要在 application.yml 中配置 steam.api-key（Steam Web API Key）.
 *
 * @see <a href="https://steamcommunity.com/dev/apikey">Steam Web API Key 申请</a>
 * @see <a href="https://partner.steamgames.com/doc/webapi">Steam Web API 文档</a>
 */
public class SteamTools {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Steam Web API 基础地址
     */
    private static final String STEAM_API_BASE = "https://api.steampowered.com";

    /**
     * Steam 商店 API 基础地址（游戏详情）
     */
    private static final String STORE_API_BASE = "https://store.steampowered.com/api";

    // ==================== 工具方法 ====================

    /**
     * 从配置获取 Steam API Key.
     */
    private static String getApiKey() {
        String key = ConfigLoader.get().getString("steam.api-key", "");
        if (key == null || key.isBlank() || key.contains("${")) {
            return null;
        }
        return key;
    }

    /**
     * 发送 HTTP GET 请求.
     */
    private static String httpGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "MinimumJavaAgent/1.0")
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode());
        }

        return response.body();
    }

    /**
     * 获取游戏名称（用于展示）.
     */
    private static String getGameName(String appId) {
        try {
            String url = STORE_API_BASE + "/appdetails?appids=" + appId + "&filters=basic";
            String responseBody = httpGet(url);
            JsonNode root = JSON.readTree(responseBody);
            return root.path(appId).path("data").path("name").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取用户 Steam 等级.
     */
    private static int getSteamLevel(String steamId) {
        try {
            String apiKey = getApiKey();
            if (apiKey == null) return -1;

            String url = STEAM_API_BASE + "/IPlayerService/GetSteamLevel/v0001/"
                    + "?key=" + apiKey
                    + "&steamid=" + steamId
                    + "&format=json";

            String responseBody = httpGet(url);
            JsonNode root = JSON.readTree(responseBody);
            return root.path("response").path("player_level").asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 格式化分钟数为可读时长.
     */
    private static String formatPlaytime(int minutes) {
        if (minutes <= 0) return "0分钟";
        if (minutes < 60) return minutes + "分钟";

        int hours = minutes / 60;
        int mins = minutes % 60;

        if (hours < 24) {
            return mins > 0 ? hours + "小时" + mins + "分钟" : hours + "小时";
        }

        int days = hours / 24;
        int remainHours = hours % 24;
        StringBuilder sb = new StringBuilder();
        sb.append(days).append("天");
        if (remainHours > 0) sb.append(remainHours).append("小时");
        if (mins > 0) sb.append(mins).append("分钟");
        return sb.toString();
    }

    /**
     * 清理 HTML 标签，提取纯文本.
     * 处理 Steam API 返回的 HTML 内容，保留文本结构。
     */
    private static String cleanHtml(String html) {
        if (html == null || html.isEmpty()) return "";

        String text = html;
        // 1. 将块级标签替换为换行，保留段落结构
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</p>", "\n\n");
        text = text.replaceAll("(?i)</div>", "\n");
        text = text.replaceAll("(?i)</li>", "\n");
        text = text.replaceAll("(?i)<h[1-6][^>]*>", "\n");
        text = text.replaceAll("(?i)</h[1-6]>", "\n");
        // 2. 列表项前加标记
        text = text.replaceAll("(?i)<li[^>]*>", "\n• ");
        // 3. 去除所有剩余 HTML 标签
        text = text.replaceAll("<[^>]*>", "");
        // 4. 解码常见 HTML 实体
        text = text.replace("&amp;", "&");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&quot;", "\"");
        text = text.replace("&#39;", "'");
        text = text.replace("&nbsp;", " ");
        text = text.replace("&ensp;", " ");
        text = text.replace("&emsp;", "  ");
        // 5. 规范化空白
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        // 压缩连续空行为最多两个换行
        text = text.replaceAll("\n{3,}", "\n\n");
        // 去除每行首尾空格
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(trimmed);
            } else if (sb.length() > 0 && !sb.toString().endsWith("\n\n")) {
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    /**
     * 从 JsonNode 数组提取字符串列表.
     */
    private static List<String> getStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                list.add(item.asText(""));
            }
        }
        return list;
    }

    /**
     * 从 JsonNode 数组提取 description 字段列表.
     */
    private static List<String> getDescriptions(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                String desc = item.path("description").asText("");
                if (!desc.isEmpty()) {
                    list.add(desc);
                }
            }
        }
        return list;
    }

    /**
     * 获取用户最近两周的游戏使用情况.
     */
    @Tool(name = "steamGetRecentGames", description = "获取Steam用户最近两周的游戏使用情况，包括游戏名称、最近两周游玩时长和总游玩时长。返回最近玩过的游戏列表。")
    public String getRecentGames(
            @Param(name = "steamId", description = "用户的64位Steam ID（例如 76561198012345678）") String steamId,
            @Param(name = "count", description = "返回的游戏数量，默认返回全部", required = false) Integer count
    ) {
        String apiKey = getApiKey();
        if (apiKey == null) return "[ERROR] 未配置 Steam API Key，请在 application.yml 中设置 steam.api-key";

        try {
            String url = STEAM_API_BASE + "/IPlayerService/GetRecentlyPlayedGames/v0001/"
                    + "?key=" + apiKey
                    + "&steamid=" + steamId
                    + "&format=json";

            String responseBody = httpGet(url);
            JsonNode root = JSON.readTree(responseBody);
            JsonNode games = root.path("response").path("games");

            if (games.isMissingNode() || !games.isArray() || games.isEmpty()) {
                return "该用户最近两周没有游戏活动记录。";
            }

            List<String> results = new ArrayList<>();
            results.add("🎮 最近两周游戏使用情况\n");

            int limit = (count != null && count > 0) ? Math.min(count, games.size()) : games.size();
            int totalRecentMinutes = 0;

            for (int i = 0; i < limit; i++) {
                JsonNode game = games.get(i);
                String name = game.path("name").asText("未知游戏");
                int recentMinutes = game.path("playtime_2weeks").asInt(0);
                int totalMinutes = game.path("playtime_forever").asInt(0);
                int appId = game.path("appid").asInt(0);

                totalRecentMinutes += recentMinutes;

                results.add(String.format("%d. %s (AppID: %d)", i + 1, name, appId));
                results.add(String.format("   - 最近两周: %s", formatPlaytime(recentMinutes)));
                results.add(String.format("   - 总时长: %s", formatPlaytime(totalMinutes)));
            }

            results.add("");
            results.add("📊 最近两周总计游玩: " + formatPlaytime(totalRecentMinutes));
            results.add("共 " + games.size() + " 款游戏" + (limit < games.size() ? "（显示前 " + limit + " 款）" : ""));

            return String.join("\n", results);
        } catch (Exception e) {
            return "[ERROR] 查询最近游戏失败: " + e.getMessage();
        }
    }

    /**
     * 获取用户拥有的游戏列表及游玩时长.
     */
    @Tool(name = "steamGetOwnedGames", description = "获取Steam用户拥有的所有游戏列表，包含每款游戏的总游玩时长。可用于查看游戏库全貌和时长统计。")
    public String getOwnedGames(
            @Param(name = "steamId", description = "用户的64位Steam ID") String steamId,
            @Param(name = "count", description = "返回的游戏数量，默认20", required = false) Integer count,
            @Param(name = "sortByPlaytime", description = "是否按游玩时长降序排列，默认true", required = false) Boolean sortByPlaytime
    ) {
        String apiKey = getApiKey();
        if (apiKey == null) return "[ERROR] 未配置 Steam API Key，请在 application.yml 中设置 steam.api-key";

        try {
            String url = STEAM_API_BASE + "/IPlayerService/GetOwnedGames/v0001/"
                    + "?key=" + apiKey
                    + "&steamid=" + steamId
                    + "&include_appinfo=1"
                    + "&include_played_free_games=1"
                    + "&format=json";

            String responseBody = httpGet(url);
            JsonNode root = JSON.readTree(responseBody);
            JsonNode games = root.path("response").path("games");

            if (games.isMissingNode() || !games.isArray() || games.isEmpty()) {
                return "未找到该游戏库信息（可能是隐私设置限制）。";
            }

            int gameCount = root.path("response").path("game_count").asInt(games.size());
            int limit = (count != null && count > 0) ? Math.min(count, games.size()) : Math.min(20, games.size());
            boolean sort = sortByPlaytime == null || sortByPlaytime;

            // 收集到列表中以便排序
            List<GameInfo> gameList = new ArrayList<>();
            for (JsonNode game : games) {
                gameList.add(new GameInfo(
                        game.path("appid").asInt(0),
                        game.path("name").asText("未知游戏"),
                        game.path("playtime_forever").asInt(0)
                ));
            }

            if (sort) {
                gameList.sort((a, b) -> Integer.compare(b.playtimeMinutes, a.playtimeMinutes));
            }

            List<String> results = new ArrayList<>();
            results.add("🎮 用户游戏库（共 " + gameCount + " 款游戏）\n");

            int totalMinutes = 0;
            for (int i = 0; i < limit; i++) {
                GameInfo g = gameList.get(i);
                totalMinutes += g.playtimeMinutes;
                results.add(String.format("%d. %s (AppID: %d) — %s",
                        i + 1, g.name, g.appId, formatPlaytime(g.playtimeMinutes)));
            }

            results.add("");
            results.add("📊 统计信息:");
            results.add("   - 显示游戏数: " + limit + " / " + gameCount);
            results.add("   - 显示游戏总时长: " + formatPlaytime(totalMinutes));

            return String.join("\n", results);
        } catch (Exception e) {
            return "[ERROR] 查询游戏库失败: " + e.getMessage();
        }
    }

    /**
     * 获取用户在指定游戏中的成就完成情况.
     */
    @Tool(name = "steamGetAchievements", description = "获取Steam用户在指定游戏中的成就完成情况，包括已解锁和未解锁的成就列表。需要游戏支持成就系统。")
    public String getAchievements(
            @Param(name = "steamId", description = "用户的64位Steam ID") String steamId,
            @Param(name = "appId", description = "游戏的Steam App ID（例如 CS2 是 730）") String appId
    ) {
        String apiKey = getApiKey();
        if (apiKey == null) return "[ERROR] 未配置 Steam API Key，请在 application.yml 中设置 steam.api-key";

        try {
            // 先获取游戏名称
            String gameName = getGameName(appId);

            String url = STEAM_API_BASE + "/ISteamUserStats/GetPlayerAchievements/v0001/"
                    + "?key=" + apiKey
                    + "&steamid=" + steamId
                    + "&appid=" + appId
                    + "&format=json";

            String responseBody = httpGet(url);
            JsonNode root = JSON.readTree(responseBody);

            // 检查是否有错误
            if (root.has("playerstats") && root.path("playerstats").has("error")) {
                String error = root.path("playerstats").path("error").asText();
                return "[ERROR] 查询成就失败: " + error + "（可能是游戏不支持成就或隐私设置限制）";
            }

            JsonNode stats = root.path("playerstats");
            JsonNode achievements = stats.path("achievements");

            if (achievements.isMissingNode() || !achievements.isArray()) {
                return "该游戏没有成就数据或用户未游玩过该游戏。";
            }

            List<AchievementInfo> unlocked = new ArrayList<>();
            List<AchievementInfo> locked = new ArrayList<>();

            for (JsonNode ach : achievements) {
                String name = ach.path("name").asText("未知成就");
                int achieved = ach.path("achieved").asInt(0);
                // 尝试获取本地化名称（如果有）
                String displayName = ach.has("apiname") ? ach.path("apiname").asText(name) : name;

                AchievementInfo info = new AchievementInfo(displayName, achieved == 1);
                if (achieved == 1) {
                    unlocked.add(info);
                } else {
                    locked.add(info);
                }
            }

            int total = unlocked.size() + locked.size();
            double percent = total > 0 ? (unlocked.size() * 100.0 / total) : 0;

            List<String> results = new ArrayList<>();
            results.add("🏆 成就完成情况" + (gameName != null ? " — " + gameName : "") + " (AppID: " + appId + ")");
            results.add(String.format("完成进度: %d / %d (%.1f%%)\n", unlocked.size(), total, percent));

            if (!unlocked.isEmpty()) {
                results.add("✅ 已解锁 (" + unlocked.size() + "):");
                for (AchievementInfo ach : unlocked) {
                    results.add("   • " + ach.name);
                }
            }

            if (!locked.isEmpty()) {
                results.add("");
                results.add("🔒 未解锁 (" + locked.size() + "):");
                for (AchievementInfo ach : locked) {
                    results.add("   • " + ach.name);
                }
            }

            return String.join("\n", results);
        } catch (Exception e) {
            return "[ERROR] 查询成就失败: " + e.getMessage();
        }
    }

    /**
     * 获取成就 Schema（游戏所有成就的定义，不依赖用户).
     */
    @Tool(name = "steamGetGameSchema", description = "获取游戏的成就Schema定义，列出游戏所有可能的成就。不需要用户Steam ID，可用于查看游戏有哪些成就。")
    public String getGameSchema(
            @Param(name = "appId", description = "游戏的Steam App ID") String appId
    ) {
        String apiKey = getApiKey();
        if (apiKey == null) return "[ERROR] 未配置 Steam API Key，请在 application.yml 中设置 steam.api-key";

        try {
            String gameName = getGameName(appId);

            String url = STEAM_API_BASE + "/ISteamUserStats/GetSchemaForGame/v2/"
                    + "?key=" + apiKey
                    + "&appid=" + appId
                    + "&format=json";

            String responseBody = httpGet(url);
            JsonNode root = JSON.readTree(responseBody);
            JsonNode game = root.path("game");
            JsonNode achievements = game.path("availableGameStats").path("achievements");

            if (achievements.isMissingNode() || !achievements.isArray() || achievements.isEmpty()) {
                return "该游戏没有成就系统或 AppID 不正确。";
            }

            List<String> results = new ArrayList<>();
            results.add("📋 游戏成就列表" + (gameName != null ? " — " + gameName : "") + " (AppID: " + appId + ")");
            results.add("共 " + achievements.size() + " 个成就\n");

            int i = 1;
            for (JsonNode ach : achievements) {
                String name = ach.path("displayName").asText(ach.path("name").asText("未知"));
                String desc = ach.path("description").asText("");
                boolean hidden = ach.path("hidden").asInt(0) == 1;

                results.add(String.format("%d. %s%s", i++, name, hidden ? " [隐藏]" : ""));
                if (!desc.isEmpty()) {
                    results.add("   " + desc);
                }
            }

            return String.join("\n", results);
        } catch (Exception e) {
            return "[ERROR] 查询成就 Schema 失败: " + e.getMessage();
        }
    }

    /**
     * 获取玩家基本资料.
     */
    @Tool(name = "steamGetPlayerProfile", description = "获取Steam玩家的基本资料信息，包括昵称、头像、个人资料状态、Steam等级等。")
    public String getPlayerProfile(
            @Param(name = "steamId", description = "用户的64位Steam ID") String steamId
    ) {
        String apiKey = getApiKey();
        if (apiKey == null) return "[ERROR] 未配置 Steam API Key，请在 application.yml 中设置 steam.api-key";

        try {
            // 获取玩家摘要
            String url = STEAM_API_BASE + "/ISteamUser/GetPlayerSummaries/v0002/"
                    + "?key=" + apiKey
                    + "&steamids=" + steamId
                    + "&format=json";

            String responseBody = httpGet(url);
            JsonNode root = JSON.readTree(responseBody);
            JsonNode players = root.path("response").path("players");

            if (players.isMissingNode() || !players.isArray() || players.isEmpty()) {
                return "未找到该 Steam ID 对应的玩家信息。";
            }

            JsonNode player = players.get(0);

            String personaName = player.path("personaname").asText("未知");
            String profileUrl = player.path("profileurl").asText("");
            int profileState = player.path("communityvisibilitystate").asInt(0);
            int avatarState = player.path("personastate").asInt(0);
            String country = player.path("loccountrycode").asText("");

            // 获取 Steam 等级
            int steamLevel = getSteamLevel(steamId);

            List<String> results = new ArrayList<>();
            results.add("👤 Steam 玩家资料\n");
            results.add("昵称: " + personaName);
            results.add("Steam ID: " + steamId);
            results.add("个人主页: " + profileUrl);
            results.add("Steam 等级: " + (steamLevel >= 0 ? String.valueOf(steamLevel) : "未知"));

            // 资料可见性
            String visibility = switch (profileState) {
                case 1 -> "私密";
                case 2 -> "仅好友可见";
                case 3 -> "公开";
                default -> "未知";
            };
            results.add("资料可见性: " + visibility);

            // 在线状态
            String onlineStatus = switch (avatarState) {
                case 0 -> "离线";
                case 1 -> "在线";
                case 2 -> "忙碌";
                case 3 -> "离开";
                case 4 -> "打盹";
                case 5 -> "想交易";
                case 6 -> "想玩游戏";
                default -> "未知";
            };
            results.add("在线状态: " + onlineStatus);

            if (!country.isEmpty()) {
                results.add("国家/地区: " + country);
            }

            return String.join("\n", results);
        } catch (Exception e) {
            return "[ERROR] 查询玩家资料失败: " + e.getMessage();
        }
    }

    /**
     * 查询游戏详情（商店信息）.
     */
    @Tool(name = "steamGetGameDetails", description = "获取Steam商店中游戏的详细信息，包括价格、评价数量、发行日期、开发商、游戏简介、详细描述、关于游戏等。")
    public String getGameDetails(
            @Param(name = "appId", description = "游戏的Steam App ID") String appId,
            @Param(name = "language", description = "语言，默认schinese(简体中文)", required = false) String language
    ) {
        try {
            String lang = (language != null && !language.isEmpty()) ? language : "schinese";
            String url = STORE_API_BASE + "/appdetails?appids=" + appId + "&l=" + lang;

            String responseBody = httpGet(url);
            JsonNode root = JSON.readTree(responseBody);
            JsonNode appData = root.path(appId);

            if (!appData.path("success").asBoolean(false)) {
                return "[ERROR] 未找到 AppID " + appId + " 对应的游戏。";
            }

            JsonNode data = appData.path("data");
            String name = data.path("name").asText("未知游戏");
            String type = data.path("type").asText("未知");
            String shortDesc = data.path("short_description").asText("");
            String detailedDesc = data.path("detailed_description").asText("");
            String aboutTheGame = data.path("about_the_game").asText("");
            String releaseDate = data.path("release_date").path("date").asText("未知");
            boolean isFree = data.path("is_free").asBoolean(false);

            // 价格信息（详细）
            JsonNode priceInfo = data.path("price_overview");
            String priceStr;
            if (isFree) {
                priceStr = "免费游戏";
            } else if (!priceInfo.isMissingNode()) {
                String currency = priceInfo.path("currency").asText("");
                String initialPrice = priceInfo.path("initial_formatted").asText("");
                String finalPrice = priceInfo.path("final_formatted").asText("未知");
                int discountPercent = priceInfo.path("discount_percent").asInt(0);
                if (discountPercent > 0) {
                    priceStr = finalPrice + "（原价: " + initialPrice + ", 折扣: -" + discountPercent + "%）";
                } else {
                    priceStr = finalPrice;
                }
            } else {
                priceStr = "暂无定价";
            }

            // 评价数量
            JsonNode recommendations = data.path("recommendations");
            int reviewCount = recommendations.path("total").asInt(0);
            String reviewStr = reviewCount > 0 ? String.format("%,d", reviewCount) + " 条用户评价" : "暂无评价";

            // Metacritic 分数
            JsonNode metacritic = data.path("metacritic");
            String metacriticScore = metacritic.isMissingNode() ? "无" : metacritic.path("score").asText("无");

            // 开发商和发行商
            List<String> developers = getStringList(data.path("developers"));
            List<String> publishers = getStringList(data.path("publishers"));

            // 平台支持
            JsonNode platforms = data.path("platforms");
            List<String> supportedPlatforms = new ArrayList<>();
            if (platforms.path("windows").asBoolean(false)) supportedPlatforms.add("Windows");
            if (platforms.path("mac").asBoolean(false)) supportedPlatforms.add("Mac");
            if (platforms.path("linux").asBoolean(false)) supportedPlatforms.add("Linux");

            // 类别和标签
            List<String> genreList = getDescriptions(data.path("genres"));
            List<String> categoryList = getDescriptions(data.path("categories"));

            // 构建输出
            List<String> results = new ArrayList<>();
            results.add("🎮 游戏详情 — " + name + "\n");

            // 基本信息
            results.add("━━━ 基本信息 ━━━");
            results.add("App ID: " + appId);
            results.add("类型: " + type);
            results.add("发行日期: " + releaseDate);
            if (!developers.isEmpty()) {
                results.add("开发商: " + String.join(", ", developers));
            }
            if (!publishers.isEmpty()) {
                results.add("发行商: " + String.join(", ", publishers));
            }

            // 价格与评价
            results.add("\n━━━ 价格与评价 ━━━");
            results.add("价格: " + priceStr);
            results.add("用户评价: " + reviewStr);
            results.add("Metacritic 评分: " + metacriticScore);

            // 平台与类型
            results.add("\n━━━ 平台与类型 ━━━");
            results.add("支持平台: " + String.join(", ", supportedPlatforms));
            if (!genreList.isEmpty()) {
                results.add("游戏类型: " + String.join(", ", genreList));
            }
            if (!categoryList.isEmpty()) {
                results.add("游戏分类: " + String.join(", ", categoryList));
            }

            // 简介
            if (!shortDesc.isEmpty()) {
                results.add("\n📝 游戏简介:");
                results.add(cleanHtml(shortDesc));
            }

            // 详细描述（限制长度避免过长）
            if (!detailedDesc.isEmpty()) {
                String cleaned = cleanHtml(detailedDesc);
                if (cleaned.length() > 1500) {
                    cleaned = cleaned.substring(0, 1500) + "...";
                }
                results.add("\n📖 详细描述:");
                results.add(cleaned);
            }

            // 关于游戏
            if (!aboutTheGame.isEmpty()) {
                String cleaned = cleanHtml(aboutTheGame);
                if (cleaned.length() > 1500) {
                    cleaned = cleaned.substring(0, 1500) + "...";
                }
                results.add("\nℹ️ 关于游戏:");
                results.add(cleaned);
            }

            return String.join("\n", results);
        } catch (Exception e) {
            return "[ERROR] 查询游戏详情失败: " + e.getMessage();
        }
    }

    // ==================== 内部数据类 ====================

    private static class GameInfo {
        final int appId;
        final String name;
        final int playtimeMinutes;

        GameInfo(int appId, String name, int playtimeMinutes) {
            this.appId = appId;
            this.name = name;
            this.playtimeMinutes = playtimeMinutes;
        }
    }

    private static class AchievementInfo {
        final String name;
        final boolean unlocked;

        AchievementInfo(String name, boolean unlocked) {
            this.name = name;
            this.unlocked = unlocked;
        }
    }
}
