package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import com.stupidbeauty.sisterfuture.shopping.ShoppingListManager;
import com.stupidbeauty.sisterfuture.shopping.ShoppingItem;
import java.util.List;
import java.util.Map;
import ai.api.Tool;
import java.util.HashMap;

/**
 * A simple tool to list all shopping items.
 */
public class ListShoppingItemsTool implements Tool {
    private Context context;
    private ShoppingListManager shoppingListManager;

    public ListShoppingItemsTool(Context context) {
        this.context = context;
        this.shoppingListManager = new ShoppingListManager(context);
    }

    @Override
    public String getName() {
        return "list_shopping_items";
    }

    @Override
    public String getDescription() {
        return "List all items in the shopping list.";
    }
    
    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        List<ShoppingItem> items = shoppingListManager.getAllItems();
        
        // Convert to serializable map
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("count", items.size());
        result.put("message", "Successfully retrieved " + items.size() + " item(s)." );
        
        return result;
    }
}