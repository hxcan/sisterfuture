from tools.base import BaseTool
import requests

class SearchWithBrave(BaseTool):
    """
    使用 Brave Search API 执行网络搜索
    支持 text/raw/summary 模式
    """
    name = "search_with_brave"
    description = "通过 Brave Search API 进行安全稳定的网页搜索，支持多种返回模式"
    
    def __init__(self, api_key: str):
        super().__init__()
        self.api_key = api_key
        self.headers = {
            'Accept': 'application/json',
            'X-Subscription-Token': self.api_key
        }
        self.url = 'https://api.search.brave.com/res/v1/web/search'
        
    def _format_result(self, item, mode='text'):
        title = item.get('title', '')
        url = item.get('url', '')
        
        if mode == 'raw':
            return {'title': title, 'url': url, 'raw': item}
        
        snippet = item.get('description', '')
        if mode == 'summary':
            # 可集成摘要模型
            return {'title': title, 'url': url, 'summary': snippet[:200]}
            
        return f"{title}\n{snippet}\n[链接]({url})\n---"

    def run(self, query: str, mode: str = 'text', count: int = 5):
        params = {
            'q': query,
            'count': count
        }
        
        try:
            resp = requests.get(self.url, headers=self.headers, params=params, timeout=10)
            resp.raise_for_status()
            data = resp.json()
            
            results = data.get('web', {}).get('results', [])
            formatted = [self._format_result(r, mode) for r in results]
            
            return {"results": formatted} if mode == 'raw' else "\n".join(formatted)
            
        except Exception as e:
            return f"Brave Search 请求失败: {str(e)}"}