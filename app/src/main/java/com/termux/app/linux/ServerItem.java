package com.termux.app.linux;

import org.json.JSONException;
import org.json.JSONObject;

public class ServerItem {

    public final String id;
    public final String name;
    public final String description;
    public final int port;
    public final String startCommand;
    public final String stopCommand;
    public final String processPattern;
    public final boolean custom;

    public ServerItem(String id, String name, String description, int port, String startCommand,
                      String stopCommand, String processPattern, boolean custom) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.port = port;
        this.startCommand = startCommand;
        this.stopCommand = stopCommand;
        this.processPattern = processPattern;
        this.custom = custom;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("description", description);
        object.put("port", port);
        object.put("start", startCommand);
        object.put("stop", stopCommand);
        object.put("pattern", processPattern);
        return object;
    }

    public static ServerItem fromJson(JSONObject object) throws JSONException {
        return new ServerItem(object.getString("id"), object.getString("name"),
            object.optString("description"), object.optInt("port"), object.getString("start"),
            object.getString("stop"), object.getString("pattern"), true);
    }

}
