#!/usr/bin/env python3
"""Crawl public KREAM category pages into an idempotent products seed SQL file."""

from __future__ import annotations

import argparse
import html
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import urllib.robotparser
from dataclasses import dataclass, replace
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path


BASE_URL = "https://kream.co.kr"
ROBOTS_URL = f"{BASE_URL}/robots.txt"
USER_AGENT = "NeedUProductCrawler/1.0"
PRODUCTS_PER_CATEGORY = 200
MAX_PAGES_PER_CATEGORY = 20

# KREAM's public top-level categories mapped to ProductCategory codes.
CATEGORIES = (
    (44, "신발", "FASHION"),
    (46, "패션잡화", "FASHION"),
    (48, "테크", "DIGITAL"),
    (49, "아우터", "FASHION"),
    (50, "상의", "FASHION"),
    (51, "하의", "FASHION"),
    (53, "지갑", "FASHION"),
    (54, "컬렉터블", "LIVING"),
    (55, "가구/리빙", "LIVING"),
    (63, "가방", "FASHION"),
    (64, "시계", "FASHION"),
    (65, "뷰티", "BEAUTY"),
    (66, "캠핑", "SPORTS"),
    (71, "트레이딩 카드", "BOOKS_TICKETS"),
    (73, "가전", "DIGITAL"),
    (74, "푸드", "FOOD"),
    # 기간이 있는 공연·전시·콘서트 티켓은 상품 데이터에서 제외한다.
    (77, "금/은", "LUXURY"),
)


@dataclass(frozen=True)
class ProductRow:
    external_id: str
    name: str
    category: str
    price: int
    image_url: str
    purchase_url: str


class ProductCardParser(HTMLParser):
    _VOID_TAGS = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}

    def __init__(self, category: str):
        super().__init__(convert_charrefs=True)
        self.category = category
        self.products: list[ProductRow] = []
        self._depth = 0
        self._card_depth: int | None = None
        self._external_id: str | None = None
        self._name: str | None = None
        self._image_url: str | None = None
        self._price_depth: int | None = None
        self._price_text: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag not in self._VOID_TAGS:
            self._depth += 1
        attributes = dict(attrs)

        if tag == "a" and self._card_depth is None:
            classes = (attributes.get("class") or "").split()
            match = re.fullmatch(r"/products/(\d+)", attributes.get("href") or "")
            if "product_card" in classes and match:
                self._card_depth = self._depth
                self._external_id = match.group(1)
                self._name = None
                self._image_url = None
                self._price_depth = None
                self._price_text = []
                return

        if self._card_depth is None:
            return

        if tag == "img" and not self._image_url:
            self._name = attributes.get("alt")
            self._image_url = attributes.get("src")
        elif (attributes.get("data-sdui-id") or "") == f"product_price/{self._external_id}":
            self._price_depth = self._depth

    def handle_data(self, data: str) -> None:
        if self._price_depth is not None:
            self._price_text.append(data)

    def handle_endtag(self, tag: str) -> None:
        if self._price_depth == self._depth:
            self._price_depth = None

        if tag == "a" and self._card_depth == self._depth:
            self._finish_card()
            self._card_depth = None

        self._depth -= 1

    def _finish_card(self) -> None:
        price_match = re.search(r"([\d,]+)\s*원", "".join(self._price_text))
        if not all((self._external_id, self._name, self._image_url, price_match)):
            return

        image_url = html.unescape(self._image_url or "")
        image_url = urllib.parse.urlsplit(image_url)._replace(query="").geturl()
        external_id = self._external_id or ""
        self.products.append(ProductRow(
            external_id=external_id,
            name=(self._name or "").strip()[:300],
            category=self.category,
            price=int(price_match.group(1).replace(",", "")),
            image_url=image_url[:2048],
            purchase_url=f"{BASE_URL}/products/{external_id}",
        ))


def fetch(url: str, timeout: float, retries: int = 3) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return response.read().decode(response.headers.get_content_charset() or "utf-8")
        except urllib.error.HTTPError as error:
            if error.code != 429 and error.code < 500:
                raise
            retry_after = int(error.headers.get("Retry-After", "0") or 0)
            time.sleep(max(retry_after, 2 ** attempt))
        except urllib.error.URLError:
            if attempt == retries - 1:
                raise
            time.sleep(2 ** attempt)
    raise RuntimeError(f"Failed to fetch {url}")


def load_robots(timeout: float) -> urllib.robotparser.RobotFileParser:
    robots = urllib.robotparser.RobotFileParser(ROBOTS_URL)
    robots.parse(fetch(ROBOTS_URL, timeout).splitlines())
    return robots


def parse_products(page_html: str, category: str) -> list[ProductRow]:
    parser = ProductCardParser(category)
    parser.feed(page_html)
    return [
        replace(product, name=clean_product_name(product.name))
        for product in parser.products
        if not is_used_product(product.name) and not is_preorder_product(product.name)
    ]


def is_used_product(name: str) -> bool:
    return re.search(
        r"리퍼비시|refurbish|중고|^\[(?:A등급|B등급|B)\]",
        name,
        re.IGNORECASE,
    ) is not None


def is_preorder_product(name: str) -> bool:
    return re.search(
        r"예약\s*(?:배송|발송|판매)|프리오더|pre[- ]?order",
        name,
        re.IGNORECASE,
    ) is not None


def clean_product_name(name: str) -> str:
    def remove_marketing_tag(match: re.Match[str]) -> str:
        tag = match.group(1)
        if re.search(r"KREAM\s*(?:단독|선런칭)|적립|쿠폰|사은품\s*증정|선물포장", tag, re.IGNORECASE):
            return ""
        return match.group(0)

    return re.sub(r"\[([^\]]+)\]\s*", remove_marketing_tag, name).strip()


def crawl(delay: float, timeout: float) -> tuple[list[ProductRow], dict[str, int]]:
    robots = load_robots(timeout)
    all_products: list[ProductRow] = []
    seen_ids: set[str] = set()
    counts: dict[str, int] = {}

    for category_id, category_name, product_category in CATEGORIES:
        category_products: list[ProductRow] = []
        page = 1
        while len(category_products) < PRODUCTS_PER_CATEGORY and page <= MAX_PAGES_PER_CATEGORY:
            url = f"{BASE_URL}/categories/{category_id}?page={page}"
            if not robots.can_fetch(USER_AGENT, url):
                raise PermissionError(f"robots.txt disallows {url}")
            if all_products or page > 1:
                time.sleep(delay)
            for product in parse_products(fetch(url, timeout), product_category):
                if product.external_id in seen_ids:
                    continue
                seen_ids.add(product.external_id)
                category_products.append(product)
                if len(category_products) == PRODUCTS_PER_CATEGORY:
                    break
            page += 1

        if not category_products:
            raise RuntimeError(f"{category_name}({category_id}) returned no products")
        if len(category_products) < PRODUCTS_PER_CATEGORY:
            print(
                f"warning: {category_name} has only {len(category_products)} unique public products",
                flush=True,
            )
        all_products.extend(category_products)
        counts[category_name] = len(category_products)
        print(f"{category_name}: {len(category_products)}", flush=True)

    return all_products, counts


def sql_string(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def write_sql(products: list[ProductRow], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc).isoformat()
    lines = [
        "-- Generated by scripts/crawl_kream_products.py",
        f"-- Collected at {timestamp}; source: https://kream.co.kr/categories/*",
        "-- Idempotent on uk_products_platform_external_id.",
        "-- Run manually after deploying PlatformType.KREAM support.",
        "",
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
        "",
    ]
    columns = (
        "platform_type, external_id, name, category, description, price, "
        "image_url, purchase_url, status, created_at, updated_at"
    )
    for start in range(0, len(products), 100):
        chunk = products[start:start + 100]
        lines.append(f"INSERT INTO products ({columns}) VALUES")
        values = []
        for product in chunk:
            values.append(
                "(" + ", ".join((
                    "'KREAM'",
                    sql_string(product.external_id),
                    sql_string(product.name),
                    sql_string(product.category),
                    "NULL",
                    str(product.price),
                    sql_string(product.image_url),
                    sql_string(product.purchase_url),
                    "'ACTIVE'",
                    "UTC_TIMESTAMP(6)",
                    "UTC_TIMESTAMP(6)",
                )) + ")"
            )
        lines.append(",\n".join(values))
        lines.append(
            "AS incoming ON DUPLICATE KEY UPDATE "
            "name = incoming.name, category = incoming.category, price = incoming.price, "
            "image_url = incoming.image_url, purchase_url = incoming.purchase_url, "
            "status = incoming.status, updated_at = UTC_TIMESTAMP(6);\n"
        )
    lines.append("COMMIT;\n")
    output.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("src/main/resources/db/data/kream-products.sql"),
    )
    parser.add_argument("--delay", type=float, default=1.0)
    parser.add_argument("--timeout", type=float, default=30.0)
    args = parser.parse_args()

    if args.delay < 1.0:
        parser.error("--delay must be at least 1.0 second")
    products, counts = crawl(args.delay, args.timeout)
    write_sql(products, args.output)
    print(f"wrote {len(products)} products to {args.output}", flush=True)
    print(", ".join(f"{name}={count}" for name, count in counts.items()), flush=True)


if __name__ == "__main__":
    main()
