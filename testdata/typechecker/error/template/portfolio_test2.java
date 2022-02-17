//The program has cycle in inheritance
//This sould throw error

int main() {
    printInt(new Mobile().Start());
    return 0;
}

class Mobile extends Computer {
    TV tv;
    int start() {
        int result;
        printInt(100);
        tv = new Mobile();
        result = tv.switchOff();
        return result;
    }
}

class Computer extends TV {
    Mobile mobile;
    int switchOn() {
        int result;
        printInt(100);
        mobile = new Mobile();
        result = mobile.start();
        return result;
    }
}
class TV extends Mobile {
    Computer computer;
    int switchOff() {
        int result;
        printInt(100);
        computer = new Computer();
        result = computer.switchOn();
        return result;
    }
}