from flask import Flask, jsonify, make_response
import time

app = Flask(__name__)

data = [
    {
        "id": 1,
        "method": "GET",
        "url": "http://127.0.0.1:5000/page/1",
        "headers": "{}",
        "body": "",
    },
    {
        "id": 2,
        "method": "GET",
        "url": "http://127.0.0.1:5000/page/2",
        "headers": "{}",
        "body": "",
    },
    {
        "id": 3,
        "method": "GET",
        "url": "http://127.0.0.1:5000/page/3",
        "headers": "{}",
        "body": "",
    },
    {
        "id": 4,
        "method": "GET",
        "url": "http://127.0.0.1:5000/page/4",
        "headers": "{}",
        "body": "",
    }
]

@app.route("/")
def get_data():
    response = make_response( jsonify(data), 200)
    response.headers["Content-Type"] = "application/json"
    return response


@app.route("/page/<string:page_id>")
def get_page(page_id):
    response = make_response(jsonify( {"page": page_id } ), 200)
    response.headers["Content-Type"] = "application/json"
    if page_id == "3":
        time.sleep(4)
    return response


if __name__ == "__main__":
    app.run()
