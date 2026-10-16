#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#ifdef _WIN32
  #include <windows.h>
#else
  #include <unistd.h>
#endif

void wait(int milliseconds) {
  #ifdef _WIN32
    Sleep(milliseconds);
  #else
    usleep(milliseconds * 1000);
  #endif
}

int main() {
  srand((unsigned int)time(NULL));

  int current_level = 7500;
  const int MAX_CAPACITY = 10000;
  
  printf("Starting Water Sensor Agent...\n");
  printf("Format: LEVEL|current|max|timestamp\n");

  while (1) {
    int consumption = 50 + rand() % 150;
    current_level -= consumption;

    if (current_level < 0) {
      current_level = 0;
    }

    long timestamp = time(NULL);
    printf("LEVEL|%d|%d|%ld\n", current_level, MAX_CAPACITY, timestamp);
    fflush(stdout);

    wait(10000); 

    if (current_level == 0) {
      printf("SYSTEM_MSG|Tank Empty. Initiating Refill Sequence...\n");
      fflush(stdout);
      wait(5000); 
      current_level = MAX_CAPACITY;
      printf("SYSTEM_MSG|Tank Refilled to Max Capacity.\n");
      fflush(stdout);
    }
  }

  return 0;
}