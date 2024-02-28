from urllib import response

from flask import Flask, render_template, request, jsonify
from werkzeug.utils import secure_filename
from datetime import timedelta
import os

import librosa
import tensorflow as tf
import numpy as np



app = Flask(__name__)

def preprocessing(audio_file, mode):
    # we want to resample audio to 16 kHz
    sr_new = 16000  # 16kHz sample rate
    x, sr = librosa.load(audio_file, sr=sr_new)

    # padding sound
    # because duration of sound is dominantly 20 s and all of sample rate is 22050
    # we want to pad or truncated sound which is below or above 20 s respectively
    max_len = 5 * sr_new  # length of sound array = time x sample rate
    if x.shape[0] < max_len:
        # padding with zero
        pad_width = max_len - x.shape[0]
        x = np.pad(x, (0, pad_width))
    elif x.shape[0] > max_len:
        # truncated
        x = x[:max_len]

    if mode == 'mfcc':
        feature = librosa.feature.mfcc(y=x, sr=sr_new)

    elif mode == 'log_mel':
        feature = librosa.feature.melspectrogram(y=x, sr=sr_new, n_mels=128, fmax=8000)
        feature = librosa.power_to_db(feature, ref=np.max)

    return feature


def diagnosis(voice_input):
    model_path = 'saved_model/my_model'
    voice_input = voice_input
    new_model = tf.keras.models.load_model(model_path)

    # preprocessing sound
    data = preprocessing(voice_input, mode='mfcc')
    data = np.array(data)
    print(data.shape)
    data = data.reshape((20, 157, 1))
    data = np.expand_dims(data, axis=0)

    datas = np.vstack([data])

    classes = new_model.predict(datas, batch_size=10)

    # classes = new_model.predict(datas)
    idx = np.argmax(classes)
    c_names = ['Chronic Disease', 'Healthy', 'Non-Chronic Disease']

    result_prediction = format(c_names[idx])
    confidence_percentage = round(np.max(classes) * 100, 2)
    print('Result prediction: \n{}'.format(c_names[idx]))
    print('Confidence Percentage: {:.2f} %'.format(np.max(classes) * 100))

    return result_prediction, confidence_percentage
# 输出
@app.route('/')
def hello_world():
    return 'Hello World!'


# 设置允许的文件格式
ALLOWED_EXTENSIONS = set(['wav'])


def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1] in ALLOWED_EXTENSIONS


# 设置静态文件缓存过期时间
app.send_file_max_age_default = timedelta(seconds=1)


# 添加路由
@app.route('/upload', methods=['POST', 'GET'])
def upload():
    if request.method == 'POST':
        try:
            # 解析请求头中的文件名和文件流
            # 通过file标签获取文件
            file = request.files['file']
            if not (file and allowed_file(file.filename)):
                response = {
                    'status': 501,
                    'message': 'please upload file with.wav format'
                }
                return jsonify(response)
                # return jsonify({"error": 1001, "msg": "图片类型：png、PNG、jpg、JPG、bmp"})
            # 当前文件所在路径
            basepath = os.path.dirname(__file__)
            # 一定要先创建该文件夹，不然会提示没有该路径
            upload_path = os.path.join(basepath, 'uploadFiles', secure_filename(file.filename))
            # 保存文件
            file.save(upload_path)

            result_prediction, confidence_percentage = diagnosis(upload_path)
            # 返回上传成功界面
            response = {
                'status': 200,
                'message': 'upload success',
                'result_prediction': result_prediction,
                'confidence_percentage': confidence_percentage

            }
            return jsonify(response)
        except Exception as e:
            print(e)
            response = {
                'status': 500,
                'message': 'upload failed'
            }
            # 重新返回上传界面
            return jsonify(response)


if __name__ == '__main__':
    app.run(host="0.0.0.0", port=5000)
    # app.run(port=8080)
