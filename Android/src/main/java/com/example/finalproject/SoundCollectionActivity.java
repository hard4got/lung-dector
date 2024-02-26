package com.example.finalproject;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.finalproject.env.Env;
import com.example.finalproject.widget.WaveView;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.Permission;
import com.yanzhenjie.permission.PermissionListener;
import com.yanzhenjie.permission.Rationale;
import com.yanzhenjie.permission.RationaleListener;

import java.io.File;
import java.io.IOException;
import java.util.List;

import jaygoo.widget.wlv.WaveLineView;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import tech.oom.idealrecorder.IdealRecorder;
import tech.oom.idealrecorder.StatusListener;


public class SoundCollectionActivity extends AppCompatActivity {

    Button recordBtn;
    Button soundSubmit;
    WaveView waveView;
    WaveLineView waveLineView;
    TextView tips;
    TextView soundResultPrediction;
    TextView soundConfidencePercentage;

    ProgressBar soundUploadProgressBar;
    IdealRecorder idealRecorder;

    IdealRecorder.RecordConfig recordConfig;



    RationaleListener rationaleListener = new RationaleListener() {
        @Override
        public void showRequestPermissionRationale(int requestCode, final Rationale rationale) {
            com.yanzhenjie.alertdialog.AlertDialog.newBuilder(SoundCollectionActivity.this)
                    .setTitle("友好提醒")
                    .setMessage("录制声音保存录音需要录音和读取文件相关权限哦，爱给不给")
                    .setPositiveButton("好，给你", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            rationale.resume();
                        }
                    }).setNegativeButton("我是拒绝的", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            rationale.cancel();
                        }
                    }).create().show();
        }
    };


    private StatusListener statusListener = new StatusListener() {
        @Override
        public void onStartRecording() {
            waveLineView.startAnim();
            tips.setTextColor(Color.rgb(50,205,50));
            tips.setText("Start Recording");
        }

        @Override
        public void onRecordData(short[] data, int length) {

            for (int i = 0; i < length; i += 60) {
                waveView.addData(data[i]);
            }
            Log.d("MainActivity", "current buffer size is " + length);
        }

        @Override
        public void onVoiceVolume(int volume) {
            double myVolume = (volume - 40) * 4;
            waveLineView.setVolume((int) myVolume);
            Log.d("MainActivity", "current volume is " + volume);
        }

        @Override
        public void onRecordError(int code, String errorMsg) {
            tips.setText("录音错误" + errorMsg);
        }

        @Override
        public void onFileSaveFailed(String error) {
            Toast.makeText(SoundCollectionActivity.this, "文件保存失败", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onFileSaveSuccess(String fileUri) {
            Toast.makeText(SoundCollectionActivity.this, "File saved at:" + fileUri, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStopRecording() {
            tips.setTextColor(Color.RED);
            tips.setText("Recording Ends");
            waveLineView.stopAnim();
        }
    };


    private PermissionListener listener = new PermissionListener() {
        @Override
        public void onSucceed(int requestCode, List<String> grantedPermissions) {

            if (requestCode == 100) {
                record();
            }
        }

        @Override
        public void onFailed(int requestCode, List<String> deniedPermissions) {
            // 权限申请失败回调。
            if (requestCode == 100) {
                Toast.makeText(SoundCollectionActivity.this, "没有录音和文件读取权限，你自己看着办", Toast.LENGTH_SHORT).show();
            }
            if (AndPermission.hasAlwaysDeniedPermission(SoundCollectionActivity.this, deniedPermissions)) {
                AndPermission.defaultSettingDialog(SoundCollectionActivity.this, 300).show();
            }
        }

    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IdealRecorder.getInstance().init(this);
        setContentView(R.layout.activity_sound_collection);
        recordBtn = findViewById(R.id.register_record_btn);
        waveView =  findViewById(R.id.wave_view);
        waveLineView = findViewById(R.id.waveLineView);
        tips = findViewById(R.id.tips);
        soundSubmit = findViewById(R.id.sound_submit);
        soundResultPrediction = findViewById(R.id.soundResultPrediction);
        soundConfidencePercentage = findViewById(R.id.soundConfidencePercentage);
        soundUploadProgressBar = findViewById(R.id.sound_upload_progress_bar);
        idealRecorder = IdealRecorder.getInstance();

        recordBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                readyRecord();
                return true;
            }
        });
        soundSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                soundUploadProgressBar.setVisibility(View.VISIBLE);
//                String serverUrl = "http://96.43.86.34:5000/upload";
                String serverUrl = Env.serverUrl;
                String filePath = getSaveFilePath();
                try {
                    uploadFile(serverUrl, filePath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });



        recordBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                switch (action) {
                    case MotionEvent.ACTION_UP:
                        stopRecord();
                        return false;

                }
                return false;
            }
        });
        recordConfig = new IdealRecorder.RecordConfig(MediaRecorder.AudioSource.MIC, 48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

    }

    /**
     * 准备录音 录音之前 先判断是否有相关权限
     */
    private void readyRecord() {
        soundSubmit.setVisibility(View.INVISIBLE);
        AndPermission.with(this)
                .requestCode(100)
                .permission(Permission.MICROPHONE, Permission.STORAGE)
                .rationale(rationaleListener).callback(listener).start();

    }

    /**
     * 开始录音
     */
    private void record() {
        //如果需要保存录音文件  设置好保存路径就会自动保存  也可以通过onRecordData 回调自己保存  不设置 不会保存录音
        idealRecorder.setRecordFilePath(getSaveFilePath());
//        idealRecorder.setWavFormat(false);
        //设置录音配置 最长录音时长 以及音量回调的时间间隔
        idealRecorder.setRecordConfig(recordConfig).setMaxRecordTime(20000).setVolumeInterval(200);
        //设置录音时各种状态的监听
        idealRecorder.setStatusListener(statusListener);
        idealRecorder.start(); //开始录音

    }

    /**
     * 获取文件保存路径
     *
     * @return
     */
    private String getSaveFilePath() {
        File file = new File(Environment.getExternalStorageDirectory(), "Audio");
        if (!file.exists()) {
            file.mkdirs();
        }
        File wavFile = new File(file, "sounds.wav");
        return wavFile.getAbsolutePath();
    }


    /**
     * 停止录音
     */
    private void stopRecord() {
        //停止录音
        idealRecorder.stop();
        soundSubmit.setVisibility(View.VISIBLE);
    }


    public void uploadFile(String serverUrl, String filePath) throws IOException {
        File file = new File(filePath);
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
                                    soundUploadProgressBar.setVisibility(View.GONE);
                                    soundResultPrediction.setText("Result prediction: "+result_prediction);
                                    soundConfidencePercentage.setText("Confidence Percentage: "+confidence_percentage+"%");
                                    soundSubmit.setVisibility(View.INVISIBLE);
                                    Toast.makeText(getApplicationContext(), "File Upload Successful!", Toast.LENGTH_SHORT).show();

                                }
                            });
                        } else if (status == 501) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    soundUploadProgressBar.setVisibility(View.GONE);
                                    Toast.makeText(getApplicationContext(), "File Upload Failed!", Toast.LENGTH_LONG).show();


                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(), "File Upload Failed!", Toast.LENGTH_LONG).show();
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