//The overridden method signature is not matching with parent class

int main() {
    printInt(new Mobile().start(10));
    return 0;
}

class Mobile extends Computer {
    int start(int input) {
        int result;
        printInt(100);
        result = input;
        return result;
    }
}

class Computer{

    int start(int input, int power) {
        int result;
        printInt(100);
        result = power;
        return result;
    }
}