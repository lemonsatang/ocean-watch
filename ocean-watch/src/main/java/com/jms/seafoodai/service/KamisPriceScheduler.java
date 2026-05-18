package com.jms.seafoodai.service;

import com.jms.seafoodai.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Component
public class KamisPriceScheduler {

    private static final Logger log = LoggerFactory.getLogger(KamisPriceScheduler.class);
    
    private final UserMapper userMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final String certKey = "8ae7ddf5-f117-4cd3-b7e9-4f25042b48da";
    private final String certId = "7987";

    public KamisPriceScheduler(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 서버 시작 시 즉시 1회 진짜 실시간 데이터 수집 실행
     */
    @PostConstruct
    public void init() {
        log.info("🚀 KAMIS 실시간 시세 수집 스케줄러를 초기화합니다.");
        try {
            log.info("🛡️ tb_watch_trade 테이블에 fair_price_status 컬럼이 있는지 확인 및 자동 생성을 시도합니다.");
            userMapper.addFairPriceStatusColumnIfNotExists();
            log.info("🛡️ 컬럼 자동 방어 완료.");
        } catch (Exception e) {
            log.error("⚠️ 컬럼 자동 생성 중 경고 발생 (이미 존재하거나 권한 부족): {}", e.getMessage());
        }
        collectDailyPrices();
    }

    /**
     * 매일 새벽 5시에 작동하는 시세 수집 스케줄러
     */
    @Scheduled(cron = "0 0 5 * * *")
    public void scheduledCollect() {
        log.info("⏰ 매일 새벽 5시 KAMIS 시세 자동 수집 스케줄러 작동 개시");
        collectDailyPrices();
    }

    /**
     * 시세 수집 및 Upsert 수행 핵심 비즈니스 로직
     */
    public void collectDailyPrices() {
        LocalDate today = LocalDate.now();
        String priceDateStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        log.info("📊 KAMIS 실시간 데이터 수집 시작 (날짜: {})", priceDateStr);

        try {
            // 02: 도매 시세, 01: 소매 시세 조회
            Map<String, Integer> wholesalePrices = fetchPricesFromKamis("02", priceDateStr);
            Map<String, Integer> retailPrices = fetchPricesFromKamis("01", priceDateStr);

            String[] fishTypes = {"고등어", "오징어", "갈치", "새우", "넙치"};
            boolean dataFound = false;

            for (String fish : fishTypes) {
                Integer wholesale = wholesalePrices.get(fish);
                Integer retail = retailPrices.get(fish);

                if (wholesale != null || retail != null) {
                    dataFound = true;
                    Map<String, Object> paramMap = new HashMap<>();
                    paramMap.put("priceDate", priceDateStr);
                    paramMap.put("fishType", fish);
                    paramMap.put("avgWholesalePrice", wholesale != null ? wholesale : 0);
                    paramMap.put("avgRetailPrice", retail != null ? retail : 0);

                    userMapper.upsertFairPrice(paramMap);
                    log.info("✅ KAMIS 실시간 시세 적재 성공 [어종: {}, 도매: {}원, 소매: {}원]", fish, wholesale, retail);
                }
            }

            if (!dataFound) {
                log.warn("⚠️ 당일 KAMIS 실시간 데이터를 수집할 수 없거나 아직 시세 데이터가 발표되지 않았습니다. Fallback 방어 시세 데이터를 생성합니다.");
                generateAndInsertMockPrices(priceDateStr);
            }

        } catch (Exception e) {
            log.error("❌ KAMIS API 실시간 연동 중 예외 발생: {}. Fallback 방어 시세 데이터를 생성합니다.", e.getMessage());
            generateAndInsertMockPrices(priceDateStr);
        }
    }

    /**
     * KAMIS API로부터 특정 부류의 시세 정보를 호출하고 파싱함
     */
    private Map<String, Integer> fetchPricesFromKamis(String clsCode, String dateStr) {
        Map<String, Integer> priceMap = new HashMap<>();
        try {
            String url = String.format(
                "https://www.kamis.or.kr/service/price/xml.do?action=dailyPriceByCategoryList" +
                "&p_cert_key=%s&p_cert_id=%s&p_returntype=json&p_product_cls_code=%s" +
                "&p_item_category_code=600&p_regday=%s",
                certKey, certId, clsCode, dateStr
            );
            log.info("📡 KAMIS API 호출: {}", url);
            
            // KAMIS 서버의 오작동 Content-Type (text/plain)에 대처하기 위해 String으로 생짜 수신
            String responseStr = restTemplate.getForObject(url, String.class);
            if (responseStr == null || responseStr.trim().isEmpty()) {
                return priceMap;
            }
            
            // Jackson ObjectMapper를 사용하여 수동으로 Map 역직렬화
            Map<String, Object> response = objectMapper.readValue(responseStr, Map.class);
            if (response == null || !response.containsKey("data")) {
                return priceMap;
            }
            Object dataObj = response.get("data");
            if (!(dataObj instanceof Map)) {
                return priceMap;
            }
            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
            Object itemsObj = dataMap.get("item");
            if (!(itemsObj instanceof java.util.List)) {
                return priceMap;
            }
            
            java.util.List<Map<String, Object>> items = (java.util.List<Map<String, Object>>) itemsObj;
            for (Map<String, Object> item : items) {
                String countyName = (String) item.get("countyname");
                if (countyName == null || !countyName.contains("평균")) {
                    continue;
                }
                String itemName = (String) item.get("item_name");
                if (itemName == null) {
                    continue;
                }
                
                String fishType = null;
                if (itemName.contains("고등어")) fishType = "고등어";
                else if (itemName.contains("오징어")) fishType = "오징어";
                else if (itemName.contains("갈치")) fishType = "갈치";
                else if (itemName.contains("새우")) fishType = "새우";
                else if (itemName.contains("광어") || itemName.contains("넙치")) fishType = "넙치";
                
                if (fishType == null) {
                    continue;
                }
                
                String priceStr = (String) item.get("price");
                if (priceStr == null) {
                    continue;
                }
                priceStr = priceStr.replace(",", "").trim();
                if (priceStr.isEmpty() || "-".equals(priceStr)) {
                    continue;
                }
                try {
                    int priceVal = Integer.parseInt(priceStr);
                    priceMap.put(fishType, priceVal);
                } catch (NumberFormatException nfe) {
                    // ignore
                }
            }
        } catch (Exception e) {
            log.error("❌ KAMIS API 호출 중 오류 발생 (clsCode: {}): {}", clsCode, e.getMessage());
        }
        return priceMap;
    }

    /**
     * KAMIS API 호출 장애 또는 주말/공휴일 등 시세 미발표 시점을 위한 하이브리드 방어용 Mock 가격 생성
     */
    private void generateAndInsertMockPrices(String priceDateStr) {
        String[] fishTypes = {"고등어", "오징어", "갈치", "새우", "넙치"};
        Random random = new Random();

        log.info("⚡ 당일 실시간 시세 데이터가 없거나 수집 예외가 발생하여, [고등어/오징어/갈치/새우/넙치]의 기준 시세 방어 데이터를 자동 Upsert 합니다. (날짜: {})", priceDateStr);

        for (String fish : fishTypes) {
            int wholesalePrice = 0;
            int retailPrice = 0;

            switch (fish) {
                case "고등어":
                    wholesalePrice = 3200 + random.nextInt(400);
                    retailPrice = 4500 + random.nextInt(600);
                    break;
                case "오징어":
                    wholesalePrice = 5400 + random.nextInt(600);
                    retailPrice = 7900 + random.nextInt(1000);
                    break;
                case "갈치":
                    wholesalePrice = 12000 + random.nextInt(1200);
                    retailPrice = 17500 + random.nextInt(2000);
                    break;
                case "새우":
                    wholesalePrice = 17000 + random.nextInt(1800);
                    retailPrice = 26500 + random.nextInt(3000);
                    break;
                case "넙치":
                    wholesalePrice = 14500 + random.nextInt(1500);
                    retailPrice = 23000 + random.nextInt(2500);
                    break;
            }

            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("priceDate", priceDateStr);
            paramMap.put("fishType", fish);
            paramMap.put("avgWholesalePrice", wholesalePrice);
            paramMap.put("avgRetailPrice", retailPrice);

            try {
                userMapper.upsertFairPrice(paramMap);
                log.info("✅ Fallback 시세 자동 보정 성공 [어종: {}, 도매: {}원, 소매: {}원]", fish, wholesalePrice, retailPrice);
            } catch (Exception dbEx) {
                log.error("❌ Fallback 시세 DB 적재 실패 [어종: {}] 사유: {}", fish, dbEx.getMessage());
            }
        }
    }
}
