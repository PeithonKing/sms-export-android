from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import pandas as pd
import os
import uvicorn
from datetime import datetime

app = FastAPI()

class SmsMessage(BaseModel):
    sender: str
    message: str

CSV_FILE = "messages.csv"

@app.get("/")
def read_root():
    return "server is running"

@app.post("/")
def log_message(sms: SmsMessage):
    try:
        data = {
            "timestamp": [datetime.now().isoformat()],
            "sender": [sms.sender],
            "message": [sms.message]
        }
        df = pd.DataFrame(data)
        
        # Append to CSV, create if header doesn't exist
        header = not os.path.exists(CSV_FILE)
        df.to_csv(CSV_FILE, mode='a', header=header, index=False)
        
        return {"status": "success", "message": "SMS logged"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    # Host 0.0.0.0 is important to be accessible from other devices on the network
    uvicorn.run(app, host="0.0.0.0", port=5000)
