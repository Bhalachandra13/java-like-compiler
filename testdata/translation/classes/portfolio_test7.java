//This test program helps to test scope resolution  of variables

int main() {
    int x;
    int y;
    x = 5;
    y = 6;
    if(x == y) {
        int x;
        x = 0;
        int y;
        y = 8;
        {
            int x;
            int y;
            x = 0;
        }
    }
    else {
        int x;
        x = 0;
        {
            int y;
            y = 0;
        }
    }
    return 0;
}
