//This test program helps to test inheritance

int main() {
    BMW myCar;
    myCar = new BMW();
    printInt(myCar.startEngine());
    printInt(myCar.turnOnAC());
    return 0;
}

class Car {
    int startEngine() {
        printInt(100);
        return 200;
    }
}

class BMW extends Car {
    int turnOnAC(){
        printInt(1000);
        return 350;
    }
}