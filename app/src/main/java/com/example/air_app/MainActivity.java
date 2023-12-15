package com.example.air_app;


import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends AppCompatActivity {
    private EditText editTextCity;
    private TextView textViewResult;
    private Spinner spinnerStation;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private ArrayAdapter<String> stationAdapter;
    private HashMap<String, String> airInfoMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editTextCity = findViewById(R.id.editTextCity);
        textViewResult = findViewById(R.id.textViewResult);
        spinnerStation = findViewById(R.id.spinnerStation); // Spinner 참조

        stationAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        stationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStation.setAdapter(stationAdapter);

        stationAdapter.add("측정소 선택");
        spinnerStation.setSelection(stationAdapter.getPosition("측정소 선택"));

        Button buttonSearch = findViewById(R.id.buttonSearch);
        buttonSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Future<String> future = executorService.submit(new RetrieveAirTask(editTextCity.getText().toString()));
                try {
                    final String result = future.get();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String cityName = editTextCity.getText().toString();
                            // result 문자열을 파싱하여 stationname과 대기 정보를 추출
                            String[] stationInfoArray = result.split(editTextCity.getText().toString() + " ");
                            for (String stationInfo : stationInfoArray) {
                                String[] lines = stationInfo.split("\n");
                                String stationName = lines[0];
                                String airInfo = stationInfo.substring(stationName.length()).trim();
                                // stationname을 airInfoMap의 키로, 대기 정보를 값으로 저장
                                airInfoMap.put(stationName,
                                        airInfo.replace(": \n",":")
                                                .replace("coGrade","일산화탄소 지수")
                                                .replace("coValue","일산화탄소 농도(ppm)")
                                                .replace("khaiGrade","통합대기환경지수")
                                                .replace("khaiValue","통합대기환경수치")
                                                .replace("no2Grade","이산화질소 지수")
                                                .replace("no2Value","이산화질소 농도(ppm)")
                                                .replace("o3Grade","오존 지수")
                                                .replace("o3Value","오존 농도(ppm)")
                                                .replace("pm10Grade","미세먼지(pm10) 지수")
                                                .replace("pm10Value","미세먼지 농도(마이크로그램/세제곱미터)")
                                                .replace("pm25Grade","미세먼지(pm25) 지수")
                                                .replace("pm25Value","미세먼지 농도(마이크로그램/세제곱미터)")
                                                .replace("so2Grade","아황산가스 지수")
                                                .replace("so2Value","아황산가스 농도(ppm)"));
                                // stationname을 stationAdapter에 추가
                                stationAdapter.add(stationName);
                            }
                        }
                    });
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        spinnerStation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedStation = (String) parent.getItemAtPosition(position);
                textViewResult.setText(airInfoMap.get(selectedStation));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    class RetrieveAirTask implements Callable<String> {
        private String city;

        RetrieveAirTask(String city) {
            this.city = city;
        }

        @Override
        public String call() {
            // doInBackground에 있던 코드
            try {
                String encodedCity = URLEncoder.encode(city, "UTF-8");
                URL url = new URL("http://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getCtprvnRltmMesureDnsty?sidoName=" + encodedCity + "&pageNo=1&numOfRows=100&returnType=xml&serviceKey=jDq26DbkKv4OwbSIBvhBX%2BW%2B1XUMQRmtuG6LNgBYXkeTE6T8RBlP%2F2z0rGufEKODqpV3OSKCC2fkAk3MzKIhKA%3D%3D&ver=1.0");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser parser = factory.newPullParser();
                parser.setInput(new InputStreamReader(conn.getInputStream()));

                int eventType = parser.getEventType();
                boolean isStationNameFound = false;
                StringBuilder builder = new StringBuilder();
                StringBuilder itemBuilder = new StringBuilder();
                String stationName;

                HashMap<String, String> map = new HashMap<>();
                boolean inItem = false;
                String text = "";

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    String tagName = parser.getName();

                    switch (eventType) {
                        case XmlPullParser.START_TAG:
                            if (tagName.equals("item")) {
                                inItem = true;
                            }
                            break;
                        case XmlPullParser.TEXT:
                            text = parser.getText();
                            break;
                        case XmlPullParser.END_TAG:
                            if (inItem) {
                                map.put(tagName, text);
                                text = "";
                            }
                            if (tagName.equals("item")) {
                                inItem = false;
                                builder.append(map.get("sidoName")).append(" ").append(map.get("stationName")).append("\n");
                                builder.append("측정 시각").append(": ").append(map.get("dataTime")).append("\n");

                                map.remove("sidoName");
                                map.remove("stationName");
                                map.remove("dataTime");
                                map.remove("item");

                                map.entrySet().stream().sorted(Map.Entry.comparingByKey())
                                        .forEach(entry -> builder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n"));

                                                map.clear();
                            }
                            break;
                    }

                    eventType = parser.next();
                }



                return builder.toString();
            } catch (Exception e) {
                e.printStackTrace();
                return "오류가 발생했습니다."+e.getMessage();
            }
        }
    }
}

