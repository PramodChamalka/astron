import jwt, datetime, os
from dotenv import load_dotenv

load_dotenv()
SECRET = os.getenv("JWT_SECRET")

token = jwt.encode({
    "sub": "admin@astron.dev",
    "role": "Admin",
    "id": "usr-001",
    "exp": datetime.datetime.now(datetime.timezone.utc)
           + datetime.timedelta(hours=24)
}, SECRET, algorithm="HS256")

print(token)
