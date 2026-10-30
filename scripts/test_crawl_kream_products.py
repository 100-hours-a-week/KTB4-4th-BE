import tempfile
import unittest
from pathlib import Path

from crawl_kream_products import (
    CATEGORIES,
    clean_product_name,
    is_preorder_product,
    is_used_product,
    parse_products,
    write_sql,
)


class KreamProductCrawlerTest(unittest.TestCase):
    def test_parse_products_extracts_products_table_fields(self):
        page = """
        <a class="product_card" href="/products/123">
          <img alt="테스트 상품(Test Product)" src="https://img.example/p.png?type=m">
          <div data-sdui-id="product_price/123"><p>12,345원</p></div>
        </a>
        """

        products = parse_products(page, "FASHION")

        self.assertEqual(1, len(products))
        self.assertEqual("123", products[0].external_id)
        self.assertEqual("테스트 상품(Test Product)", products[0].name)
        self.assertEqual(12345, products[0].price)
        self.assertEqual("https://img.example/p.png", products[0].image_url)
        self.assertEqual("https://kream.co.kr/products/123", products[0].purchase_url)

    def test_write_sql_is_idempotent(self):
        page = """
        <a class="product_card" href="/products/123">
          <img alt="O'Brien 상품" src="https://img.example/p.png">
          <div data-sdui-id="product_price/123">10,000원</div>
        </a>
        """
        product = parse_products(page, "FASHION")[0]

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "products.sql"
            write_sql([product], output)
            sql = output.read_text(encoding="utf-8")

        self.assertIn("'KREAM'", sql)
        self.assertIn("O''Brien 상품", sql)
        self.assertIn("ON DUPLICATE KEY UPDATE", sql)
        self.assertIn("SET NAMES utf8mb4;", sql)
        self.assertIn("START TRANSACTION;", sql)
        self.assertTrue(sql.rstrip().endswith("COMMIT;"))

    def test_used_products_are_excluded_without_matching_vintage_or_slippers(self):
        self.assertTrue(is_used_product("[A등급] 리퍼비시 애플 아이폰"))
        self.assertTrue(is_used_product("Refurbished Apple iPhone"))
        self.assertTrue(is_used_product("갤럭시 중고폰"))
        self.assertTrue(is_used_product("[A등급] 공기청정기"))
        self.assertTrue(is_used_product("[B] 헤어드라이어"))
        self.assertFalse(is_used_product("빈티지 워시드 캡"))
        self.assertFalse(is_used_product("어그 타스만 슬리퍼"))
        self.assertFalse(is_used_product("[S등급] 미개봉 새상품"))

    def test_time_limited_ticket_category_is_excluded(self):
        self.assertNotIn(76, [category_id for category_id, _, _ in CATEGORIES])

    def test_preorder_products_are_excluded(self):
        self.assertTrue(is_preorder_product("[예약배송] 키링"))
        self.assertTrue(is_preorder_product("[예약 판매] 이어폰"))
        self.assertTrue(is_preorder_product("[프리오더] 포토카드 홀더"))
        self.assertTrue(is_preorder_product("Pre-order Limited Figure"))
        self.assertFalse(is_preorder_product("빠른배송 운동화"))

    def test_clean_product_name_removes_only_marketing_tags(self):
        self.assertEqual(
            "그래그 케이스(GRAGG Case)",
            clean_product_name("[5%적립/15%쿠폰] 그래그 케이스([KREAM 단독] GRAGG Case)"),
        )
        self.assertEqual(
            "[디즈니] 미키 순금카드([Disney] Mickey Gold Card)",
            clean_product_name("[디즈니] 미키 순금카드([Disney] Mickey Gold Card)"),
        )


if __name__ == "__main__":
    unittest.main()
