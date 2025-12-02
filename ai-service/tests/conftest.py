from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import pytest
from PIL import Image

from app import create_app


@dataclass(frozen=True)
class SampleImage:
    name: str
    data: bytes
    width: int
    height: int
    path: Path


@pytest.fixture()
def client():
    app = create_app({"TESTING": True})
    with app.test_client() as client:
        yield client


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
        with Image.open(path) as img:
            width, height = img.size
        return SampleImage(name=name, data=data, width=width, height=height, path=path)

    return _loader
