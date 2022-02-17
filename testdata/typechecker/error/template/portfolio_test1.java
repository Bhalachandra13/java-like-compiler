//The test program checks for scope resolution
//Error should be thrown as z is not defined in line number 14.

int main() {
     int x;
     int y;

     x = 8;
     y = x;

     {
        int z;
        z = 10;
     }
     z = 9;

    return 0;
}