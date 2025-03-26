#!/bin/bash

# Number of times to run the tests
NUM_RUNS=$1
    echo "Running $NUM_RUNS tests..."

# To capture the overall test results
FAILED_RUNS=0

# Loop to run the tests multiple times
for i in $(seq 1 $NUM_RUNS); do
    echo "Running test round $i..."
    
    # Run the tests and capture the output
    mvn clean test > result.log
    
    # Check if any tests failed by looking for "FAILURE" in the log
    if grep -q "FAILURE" result.log; then
        echo "Test round $i failed! See result.log for details."
        FAILED_RUNS=$((FAILED_RUNS + 1))
        break
    fi
done

# Output final warning if any tests failed
if [ $FAILED_RUNS -gt 0 ]; then
    echo "WARNING: $FAILED_RUNS test rounds failed. Please check the result logs for details."
else
    echo "All test rounds passed successfully!"
fi
