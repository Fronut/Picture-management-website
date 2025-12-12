from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import pytest

from app import create_app
from app.services.tagging_types import TagSuggestion


@dataclass(frozen=True)
class SampleImage:
    name: str
    data: bytes
    path: Path


class DummyBaidu:
    def __init__(self, tags: list[TagSuggestion]):
        self.tags = tags
        self.called = False

    def classify(self, *, image_bytes=None, image_url=None, limit=None):
        self.called = True
        if limit:
            return self.tags[:limit]
        return self.tags


@pytest.fixture(scope="session")
def pictures_dir() -> Path:
    current = Path(__file__).resolve()
    for ancestor in current.parents:
        candidate = ancestor / "test" / "Pictures"
        if candidate.is_dir():
            return candidate
    raise RuntimeError("Unable to locate test/Pictures directory relative to tests")


@pytest.fixture(scope="session")
def load_sample_image(pictures_dir: Path) -> Callable[[str], SampleImage]:
    def _loader(name: str) -> SampleImage:
        path = pictures_dir / name
        if not path.is_file():
            raise FileNotFoundError(f"Sample image {name} not found under {pictures_dir}")
        data = path.read_bytes()
        return SampleImage(name=name, data=data, path=path)

    return _loader


@pytest.fixture()
def dummy_baidu() -> DummyBaidu:
    tags = [
        TagSuggestion("baidu:tag1", 0.9, "baidu"),
        TagSuggestion("baidu:tag2", 0.8, "baidu"),
        TagSuggestion("baidu:tag3", 0.7, "baidu"),
    ]
    return DummyBaidu(tags)


@pytest.fixture()
def client(dummy_baidu):
    app = create_app(
        {
            "TESTING": True,
            "tagging_provider": "baidu",
            "baidu_classifier": dummy_baidu,
        }
    )
    with app.test_client() as client:
        yield client
