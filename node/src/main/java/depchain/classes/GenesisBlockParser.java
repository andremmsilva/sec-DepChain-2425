package depchain.classes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;

public class GenesisBlockParser {
    public static void main(String[] args) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            // Read JSON from file
            FileReader reader = new FileReader("initialState.json");

            // Define the expected type structure for deserialization
            Type genesisBlockType = new TypeToken<GenesisBlock>() {}.getType();

            // Deserialize JSON to GenesisBlock object
            GenesisBlock genesisBlock = gson.fromJson(reader, genesisBlockType);

            reader.close(); // Close the reader after use

            // Print parsed object
            System.out.println("Parsed Genesis Block:");
            System.out.println("Block Hash: " + genesisBlock.getHash());
            System.out.println("State: " + gson.toJson(genesisBlock.getState()));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}