#!/usr/bin/env python3
"""
create_pull_request tool - Automate GitHub Pull Request creation
"""

import requests
import json
from typing import Optional, List, Dict, Any


def create_pull_request(
    owner: str,
    repo: str,
    title: str,
    head: str,
    base: str = "master",
    body: Optional[str] = None,
    draft: bool = False,
    reviewers: Optional[List[str]] = None,
    assignees: Optional[List[str]] = None,
    labels: Optional[List[str]] = None,
    token: Optional[str] = None
) -> Dict[str, Any]:
    """Create GitHub Pull Request"""
    
    if not token:
        import os
        token = os.getenv("GITHUB_TOKEN")
    
    api_url = f"https://api.github.com/repos/{owner}/{repo}/pulls"
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    pr_data = {
        "title": title,
        "head": head,
        "base": base,
        "body": body or "",
        "draft": draft
    }
    
    try:
        response = requests.post(api_url, headers=headers, json=pr_data)
        
        if response.status_code == 201:
            pr_info = response.json()
            return {
                "success": True,
                "pr_number": pr_info["number"],
                "pr_url": pr_info["html_url"],
                "logs": [f"PR #{pr_info['number']} created successfully"]
            }
        else:
            return {
                "success": False,
                "error": response.json().get("message", "Unknown error"),
                "status_code": response.status_code
            }
    except Exception as e:
        return {"success": False, "error": str(e)}


TOOL_DEFINITION = {
    "name": "create_pull_request",
    "description": "Create GitHub Pull Request via API",
    "required": ["owner", "repo", "title", "head", "base"]
}
