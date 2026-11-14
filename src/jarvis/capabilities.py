from pathlib import Path
import json
import os
from datetime import datetime

WORKSPACE = Path(os.getenv('JARVIS_WORKSPACE', str(Path.home() / 'JARVIS_WORKSPACE'))).expanduser().resolve()
CONNECTORS = WORKSPACE / 'connectors.json'
WORKSPACE.mkdir(parents=True, exist_ok=True)


def safe_path(relative: str) -> Path:
    p = (WORKSPACE / relative).resolve()
    if p != WORKSPACE and WORKSPACE not in p.parents:
        raise ValueError('ワークスペース外への操作は禁止されています')
    return p


def capabilities():
    return {
        'chat': {'enabled': True, 'mode': 'local_llm_or_provider'},
        'memory': {'enabled': True, 'mode': 'sqlite'},
        'files': {'enabled': True, 'mode': 'approved_workspace_only'},
        'code': {'enabled': True, 'mode': 'write_only_until_approved'},
        'connectors': {'enabled': True, 'mode': 'user_configured'},
        'automation': {'enabled': True, 'mode': 'explicit_schedule_and_approval'},
        'image_generation': {'enabled': False, 'mode': 'provider_required'},
        'video_generation': {'enabled': False, 'mode': 'provider_or_pc_required'},
    }


def write_file(relative: str, content: str, kind: str = 'text'):
    if len(content) > 2_000_000:
        raise ValueError('1ファイル2MBを超える内容は分割してください')
    path = safe_path(relative)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    return {'path': str(path), 'kind': kind, 'bytes': path.stat().st_size, 'written_at': datetime.now().isoformat(timespec='seconds')}


def list_connectors():
    if not CONNECTORS.exists():
        return []
    try:
        return json.loads(CONNECTORS.read_text(encoding='utf-8'))
    except json.JSONDecodeError:
        return []


def save_connector(name: str, kind: str, base_url: str = '', enabled: bool = False):
    items = [x for x in list_connectors() if x.get('name') != name]
    items.append({'name': name, 'kind': kind, 'base_url': base_url, 'enabled': enabled, 'secret_ref': f'JARVIS_{name.upper()}_API_KEY'})
    CONNECTORS.parent.mkdir(parents=True, exist_ok=True)
    CONNECTORS.write_text(json.dumps(items, ensure_ascii=False, indent=2), encoding='utf-8')
    return items[-1]
