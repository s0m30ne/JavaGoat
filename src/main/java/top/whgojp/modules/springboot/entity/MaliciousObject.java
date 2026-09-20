package top.whgojp.modules.springboot.entity;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

public class MaliciousObject implements Serializable {
    private static final long serialVersionUID = -4609530693199052538L;

    private String command;

    public MaliciousObject(String command) {
        this.command = command;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (command != null) {
            Runtime.getRuntime().exec(command);
        }
    }

}
