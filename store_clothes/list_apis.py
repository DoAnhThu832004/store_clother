
import json
with open('store_clothes_postman_collection.json', 'r', encoding='utf-8') as f:
    data = json.load(f)
def print_requests(items, path=''):
    for item in items:
        if 'item' in item:
            print_requests(item['item'], path + item['name'] + ' -> ')
        elif 'request' in item:
            url = item['request']['url']['raw'] if isinstance(item['request']['url'], dict) else item['request']['url']
            print(f'{item[\'name\']}: {url}')
print_requests(data.get('item', []))

