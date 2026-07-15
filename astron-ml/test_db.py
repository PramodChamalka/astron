import os
import certifi
from pymongo import MongoClient
from dotenv import load_dotenv

load_dotenv()
uri = os.getenv("MONGO_URI")

print("Connecting to MongoDB...")
client = MongoClient(
    uri,
    tlsCAFile=certifi.where()
)

# force a real connection check
client.admin.command("ping")
print("SUCCESS - connected to MongoDB Atlas!")

# show the database and collections
db = client.get_database("astron")
print("Database:", db.name)
print("Collections:", db.list_collection_names())
