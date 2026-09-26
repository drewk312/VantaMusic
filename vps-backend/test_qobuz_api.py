import os
import unittest

import httpx

from qobuz_api import QobuzClient


class QobuzClientTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.previous_id = os.environ.get("QOBUZ_APP_ID")
        self.previous_secret = os.environ.get("QOBUZ_APP_SECRET")
        os.environ["QOBUZ_APP_ID"] = "123456789"
        os.environ["QOBUZ_APP_SECRET"] = "a" * 32

    async def asyncTearDown(self):
        if self.previous_id is None:
            os.environ.pop("QOBUZ_APP_ID", None)
        else:
            os.environ["QOBUZ_APP_ID"] = self.previous_id
        if self.previous_secret is None:
            os.environ.pop("QOBUZ_APP_SECRET", None)
        else:
            os.environ["QOBUZ_APP_SECRET"] = self.previous_secret

    async def test_requires_a_user_session_for_full_streams(self):
        async with httpx.AsyncClient(transport=httpx.MockTransport(lambda _: httpx.Response(500))) as http:
            client = QobuzClient(http)
            self.assertIsNone(await client.get_stream("123", "24"))

    async def test_falls_back_from_hi_res_and_rejects_preview_urls(self):
        requested_formats = []

        def handler(request: httpx.Request) -> httpx.Response:
            format_id = request.url.params.get("format_id")
            requested_formats.append(format_id)
            if format_id == "27":
                return httpx.Response(200, json={
                    "url": "https://media.example/file.flac?range=20-30",
                    "bit_depth": 24,
                    "sampling_rate": 192,
                })
            return httpx.Response(200, json={
                "url": "https://media.example/file.flac?etsp=1800000000",
                "bit_depth": 24,
                "sampling_rate": 96,
                "mime_type": "audio/flac",
            })

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
            client = QobuzClient(http)
            client.set_auth_token("licensed-session")
            stream = await client.get_stream("123", "24")

        self.assertEqual(["27", "7"], requested_formats)
        self.assertEqual("https://media.example/file.flac?etsp=1800000000", stream["url"])
        self.assertEqual(96000, stream["sampleRateHz"])
        self.assertTrue(stream["isHiRes"])


if __name__ == "__main__":
    unittest.main()
