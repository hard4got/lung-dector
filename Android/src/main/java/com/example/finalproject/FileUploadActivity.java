package com.example.finalproject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.example.finalproject.env.Env;
public class FileUploadActivity extends AppCompatActivity {

    TextView filePathtextView;
    TextView fileUploadSuccessful;
    TextView resultPrediction;
    TextView confidencePercentage;
    ProgressBar uploadProgressBar;
    Button button;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_upload);
        filePathtextView = findViewById(R.id.fileTextView);
        fileUploadSuccessful = findViewById(R.id.fileUploadSuccessful);
        resultPrediction = findViewById(R.id.resultPrediction);
        confidencePercentage = findViewById(R.id.confidencePercentage);
        uploadProgressBar =findViewById(R.id.upload_progress_bar);
        button = findViewById(R.id.button);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

                intent.setType("*/*");

                intent.addCategory(Intent.CATEGORY_OPENABLE);

                startActivityForResult(Intent.createChooser(intent, "需要选择文件"), 1);
            }
        });

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {


            Uri uri = data.getData();

            String pathString = UriUtil.getPath(this, uri);

            filePathtextView.setText(pathString);
//            String serverUrl = "http://96.43.86.34:5000/upload";
            String serverUrl = Env.serverUrl;
            String filePath = pathString;
            try {
                uploadFile(serverUrl,filePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }


        }
    }

    public void uploadFile(String serverUrl, String filePath) throws IOException {
        File file = new File(filePath);
        uploadProgressBar.setVisibility(View.VISIBLE);
        new Thread() {
            @Override
            public void run() {
                //子线程需要做的工作
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", file.getName(),
                                RequestBody.create(MediaType.parse("application/octet-stream"), file))
                        .build();
                //设置为自己的ip地址
                Request request = new Request.Builder()
                        .url(serverUrl)
                        .post(requestBody)
                        .build();
                OkHttpClient client = new OkHttpClient();

                // 发送请求并获取响应
                Response response = null;
                try {
                    response = client.newCall(request).execute();

                } catch (IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "connecting Failed!", Toast.LENGTH_LONG).show();
                        }
                    });throw new RuntimeException(e);
                }
                // 处理响应结果
                if (response.isSuccessful()) {
                    String responseBody = null;
                    try {
                        responseBody = response.body().string();
                        JsonElement jsonElement = JsonParser.parseString(responseBody);
                        JsonObject jsonObject = jsonElement.getAsJsonObject();
                        int status = jsonObject.get("status").getAsInt();
                        System.out.println(status);
                        if (status == 200){
                            String result_prediction = jsonObject.get("result_prediction").getAsString();
                            Double confidence_percentage = jsonObject.get("confidence_percentage").getAsDouble();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    uploadProgressBar.setVisibility(View.GONE);
                                    Toast.makeText(getApplicationContext(), "File Upload Successful!", Toast.LENGTH_SHORT).show();
                                    fileUploadSuccessful.setText("File Upload Successful!");
                                    fileUploadSuccessful.setTextColor(Color.rgb(30,144,255));
                                    resultPrediction.setText("Result prediction: "+result_prediction);
                                    confidencePercentage.setText("Confidence Percentage: "+confidence_percentage+"%");
                                }
                            });
                        } else if (status == 501) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    uploadProgressBar.setVisibility(View.GONE);
                                    Toast.makeText(getApplicationContext(), "File Upload Failed!", Toast.LENGTH_LONG).show();
                                    fileUploadSuccessful.setText("File Upload Failed! Please upload the file with .wav format!");
                                    fileUploadSuccessful.setTextColor(Color.RED);
                                    resultPrediction.setText("");
                                    confidencePercentage.setText("");
                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    uploadProgressBar.setVisibility(View.GONE);
                                    Toast.makeText(getApplicationContext(), "File Upload Failed!", Toast.LENGTH_LONG).show();
                                    fileUploadSuccessful.setText("File Upload Failed!");
                                }
                            });
                        }

                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "文件上传失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                // 关闭响应
                response.close();

            }
        }.start();

    }



}