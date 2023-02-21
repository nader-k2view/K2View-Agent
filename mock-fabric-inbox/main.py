from flask import Flask, jsonify, make_response
import time

app = Flask(__name__)

data = [
    {
        "taskId": 0,
        "method": "GET",
        "url": "https://api.api-ninjas.com/v1/facts?limit=1",
        "header": "X-Api-Key,o0YRD1EHvVK53J2xFUM64A==p8o7GINrZqxBztaT",
        "body": "",
    },
    {
        "taskId": 1,
        "method": "GET",
        "url": "http://127.0.0.1:5000/page/1",
        "header": "",
        "body": "",
    },
    {
        "taskId": 2,
        "method": "GET",
        "url": "http://127.0.0.1:5000/page/2",
        "header": "",
        "body": "",
    },
    {
        "taskId": 3,
        "method": "GET",
        "url": "http://127.0.0.1:5000/page/3",
        "header": "",
        "body": "",
    },
    {
        "taskId": 4,
        "method": "GET",
        "url": "http://127.0.0.1:5000/page/4",
        "header": "",
        "body": "",
    },

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
