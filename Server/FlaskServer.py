from urllib import response

from flask import Flask, render_template, request, jsonify
from werkzeug.utils import secure_filename
from datetime import timedelta
import os

app = Flask(__name__)

# 输出
@app.route('/')
def hello_world():
    return 'Hello World!'

# 设置允许的文件格式
ALLOWED_EXTENSIONS = set(['png', 'jpg', 'JPG', 'PNG', 'bmp'])
def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1] in ALLOWED_EXTENSIONS

# 设置静态文件缓存过期时间
app.send_file_max_age_default = timedelta(seconds=1)

# 添加路由
@app.route('/upload', methods=['POST','GET'])
def upload():
    if request.method == 'POST':
        try:
            #解析请求头中的文件名和文件流
            # 通过file标签获取文件
            file = request.files['file']
            if not (file and allowed_file(file.filename)):
                response = {
                    'status': 501,
                    'message': '图片类型：png、PNG、jpg、JPG、bmp'
                }
                return jsonify(response)
                # return jsonify({"error": 1001, "msg": "图片类型：png、PNG、jpg、JPG、bmp"})
            # 当前文件所在路径
            basepath = os.path.dirname(__file__)
            # 一定要先创建该文件夹，不然会提示没有该路径
            upload_path = os.path.join(basepath, 'static/images', secure_filename(file.filename))
            # 保存文件
            file.save(upload_path)
            # 返回上传成功界面
            response = {
                'status': 200,
                'message': 'upload success'
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
    app.run(host="0.0.0.0")
    # app.run(port=8080)
