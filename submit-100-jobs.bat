@echo off
echo Submitting 100 jobs to the API...
echo Please ensure job-service is running on port 8080.
echo.

FOR /L %%i IN (1,1,100) DO (
   curl -s -X POST http://localhost:8080/api/v1/jobs -H "Content-Type: application/json" -d "{\"type\":\"BULK_TEST\",\"payload\":{\"taskNumber\":\"%%i\"}}" > nul
   echo Submitted Job %%i
)

echo.
echo All 100 jobs submitted! Go look at your workers!
