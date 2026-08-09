import json

with open('store_clothes_postman_collection.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

with open('postman_bodies.txt', 'w', encoding='utf-8') as out:
    def print_bodies(items, path=''):
        for item in items:
            if 'item' in item:
                print_bodies(item['item'], path + item['name'] + ' -> ')
            elif 'request' in item and 'body' in item['request'] and item['request']['body'].get('mode') == 'raw':
                url = item['request']['url']['raw'] if isinstance(item['request']['url'], dict) else item['request']['url']
                out.write(f"ENDPOINT: {item['name']} - {item['request']['method']} {url}\n")
                out.write(f"BODY:\n{item['request']['body']['raw']}\n---\n")

    print_bodies(data.get('item', []))
