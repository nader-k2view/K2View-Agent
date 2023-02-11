from flask import Flask, jsonify, make_response, request
import jwt
import datetime

app = Flask(__name__)

# List of authorized users
users = [
    {
        'id': 1,
        'username': 'user1',
        'password': 'password1'
    },
    {
        'id': 2,
        'username': 'user2',
        'password': 'password2'
    }
]


data = [
    {
        "id": 1,
        "method": "GET",
        "url": " http://127.0.0.1:5000/page/1",
        "expected_response_code": 200,
        "headers": [],
        "timeout": 5,
        "retries": 3,
        "interval": 30,
        "body": "",
    },
    {
        "id": 2,
        "method": "GET",
        "url": " http://127.0.0.1:5000/page/2",
        "headers": [],
        "expected_response_code": 200,
        "timeout": 5,
        "retries": 3,
        "interval": 30,
        "body": "",
    },
    {
        "id": 3,
        "method": "GET",
        "url": " http://127.0.0.1:5000/page/3",
        "headers": [],
        "expected_response_code": 200,
        "timeout": 5,
        "retries": 3,
        "interval": 30,
        "body": "",
    },

]


# Secret key for encoding JWT tokens
app.config['SECRET_KEY'] = '39e2972e2714fb6e1f13b397fc2b0baf'


# Route to handle login
@app.route('/login', methods=['POST'])
def login():
    # Get username and password from request
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')

    # Check if the user exists
    user = [user for user in users if user['username'] == username and user['password'] == password]
    if not user:
        return jsonify({'message': 'Invalid credentials'}), 401

    # Encode a JWT token for the user
    token = jwt.encode({'user_id': user[0]['id'], 'exp': datetime.datetime.utcnow() + datetime.timedelta(minutes=30)}, app.config['SECRET_KEY'])

    return jsonify({'token': token})


# Route to handle requests that require authentication
@app.route('/page/<string:page_id>', methods=['GET'])
def page():
    # Get the JWT token from the request
    token = request.headers.get('Authorization')

    # If there is no token, return an error
    if not token:
        return jsonify({'message': 'Token is missing'}), 401

    try:
        # Decode the JWT token
        data = jwt.decode(token, app.config['SECRET_KEY'])

        # Get the user from the decoded data
        user = [user for user in users if user['id'] == data['user_id']]

        # If the user doesn't exist, return an error
        if not user:
            return jsonify({'message': 'Invalid user'}), 401

        response = make_response(jsonify( {"page": page_id } ), 200)
        response.headers["Content-Type"] = "application/json"
        return response
    except:
        # If the JWT token is invalid, return an error
        return jsonify({'message': 'Invalid token'}), 401



@app.route("/")
def get_data():
    response = make_response( jsonify(data), 200)
    response.headers["Content-Type"] = "application/json"
    return response

#
# @app.route("/page/<string:page_id>")
# def get_page(page_id):
#     response = make_response(jsonify( {"page": page_id } ), 200)
#     response.headers["Content-Type"] = "application/json"
#     return response


if __name__ == "__main__":
    app.run()
