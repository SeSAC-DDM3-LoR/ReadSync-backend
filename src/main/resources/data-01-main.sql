/* 0. 기존 데이터 전체 삭제 및 ID 초기화 (연관된 모든 데이터 삭제) */
TRUNCATE TABLE
    "notices", "content_reports", "libraries", "bookmarks", "books",
    "likes", "credits", "inquiries", "friendships", "room_participants",
    "exp_logs", "reports", "levels", "carts", "book_ai_chats",
    "reviews", "comments", "reading_rooms", "order_items", "exp_rules",
    "users", "subscriptions", "user_informations", "orders", "inquiry_answers",
    "community_posts", "categories", "book_logs", "chapters", "blacklists",
    "payment_history", "credit_type", "community_comments", "chat_logs",
    "book_ai_chat_rooms", "room_invitations", "payment_methods", "refresh_tokens",
    "user_vectors", "book_vectors", "chapter_vectors",
    "subscription_plans"
    RESTART IDENTITY CASCADE;

-- [1] Levels (기초 레벨 정보)
INSERT INTO "levels" ("required_exp", "max_comment_limit", "can_upload_image") VALUES
                                                                                   (0, 30, FALSE),       -- Level 1 (시작)
                                                                                   (100, 40, FALSE),     -- Level 2
                                                                                   (200, 50, FALSE),     -- Level 3
                                                                                   (400, 60, FALSE),     -- Level 4
                                                                                   (800, 70, FALSE),     -- Level 5
                                                                                   (1600, 80, TRUE),     -- Level 6
                                                                                   (3200, 100, TRUE),    -- Level 7
                                                                                   (6400, 150, TRUE),    -- Level 8
                                                                                   (12800, 200, TRUE),   -- Level 9
                                                                                   (25600, 300, TRUE);   -- Level 10                                                                         (5000, 100, TRUE);

-- [2] Categories (도서/활동 카테고리)
INSERT INTO "categories" ("category_name") VALUES
                                               ('소설'),
                                               ('IT/과학'),
                                               ('인문');

-- [3] Exp Rules (경험치 획득 규칙 - Category 참조)
INSERT INTO "exp_rules" ("activity_type", "exp", "category_id") VALUES
                                                                    ('DAILY_ATTENDANCE', 10, NULL),
                                                                    ('READ_BOOK', 50, 1),
                                                                    ('WRITE_REVIEW', 100, NULL);

-- [4] Users (사용자 기본 정보)
-- ★★★ 수정됨: status 컬럼 추가 ('ACTIVE'로 초기화) ★★★
INSERT INTO "users" ("role", "provider", "provider_id", "login_id", "password", "status","updated_at") VALUES
                                                                                                           ('ADMIN', 'LOCAL', 'admin_01', 'admin', '$2a$10$u.wb6L3F2DUJLzJFN977w.x/YMvRsR2ocXIqOE3gRMt7MnggBtjTK', 'ACTIVE',now()),
                                                                                                           ('USER', 'GOOGLE', 'google_123', 'user_a', '$2a$10$u.wb6L3F2DUJLzJFN977w.x/YMvRsR2ocXIqOE3gRMt7MnggBtjTK', 'ACTIVE',now()),
                                                                                                           ('USER', 'KAKAO', 'kakao_456', 'user_b', '$2a$10$u.wb6L3F2DUJLzJFN977w.x/YMvRsR2ocXIqOE3gRMt7MnggBtjTK', 'ACTIVE',now()),
                                                                                                           ('USER', 'NAVER', 'naver_789', 'user_c', '$2a$10$u.wb6L3F2DUJLzJFN977w.x/YMvRsR2ocXIqOE3gRMt7MnggBtjTK', 'ACTIVE',now());

-- [5] User Informations (사용자 상세 정보 - User, Level 참조)
INSERT INTO "user_informations" ("preferred_genre", "experience", "level_id", "user_id", "user_name", "tag") VALUES
                                                                                                                 ('SF/판타지', 150, 1, 1, '관리자', to_char(floor(random() * 10000), 'fm0000')),
                                                                                                                 ('기술 서적', 1200, 2, 2, '김철수', to_char(floor(random() * 10000), 'fm0000')),
                                                                                                                 ('고전 소설', 0, 1, 3, '이영희', to_char(floor(random() * 10000), 'fm0000')),
                                                                                                                 ('로맨스', 300, 1, 4, '박민수', to_char(floor(random() * 10000), 'fm0000'));

-- [6] User Vectors (사용자 벡터 - User 참조)
INSERT INTO "user_vectors" ("user_id", "vector", "created_at") VALUES
                                                                   (1, array_fill(0, ARRAY[1024])::halfvec(1024), now()),
                                                                   (2, array_fill(0, ARRAY[1024])::halfvec(1024), now()),
                                                                   (3, array_fill(0, ARRAY[1024])::halfvec(1024), now()),
                                                                   (4, array_fill(0, ARRAY[1024])::halfvec(1024), now());

-- [7] Refresh Tokens (인증 토큰 - User 참조)
INSERT INTO "refresh_tokens" ("user_id", "token") VALUES
                                                      (1, 'ref_token_admin'),
                                                      (2, 'ref_token_user_a'),
                                                      (3, 'ref_token_user_b'),
                                                      (4, 'ref_token_user_c');

-- [8] Blacklists (차단 내역 - User 참조)
-- INSERT INTO "blacklists" ("user_id", "type", "reason", "end_date", "is_active") VALUES
--                                                                                     (3, 'SITE_BAN', '운영 정책 위반(욕설)', now() + interval '7 days', TRUE),
--                                                                                     (4, 'SITE_BAN', '도배성 댓글', now() + interval '1 days', FALSE),
--                                                                                     (2, 'SITE_BAN', '결제 오류 악용', now() + interval '3 days', TRUE);
-- [8] Blacklists (수정됨: start_date 추가)
INSERT INTO "blacklists" ("user_id", "type", "reason", "start_date", "end_date", "is_active") VALUES
                                                                                                  (3, 'SITE_BAN', '운영 정책 위반(욕설)', now(), now() + interval '7 days', TRUE),
                                                                                                  (4, 'SITE_BAN', '도배성 댓글', now(), now() + interval '1 days', FALSE),
                                                                                                  (2, 'SITE_BAN', '결제 오류 악용', now(), now() + interval '3 days', TRUE);

-- [9] Books (도서 정보 - Category 참조) 260203 수정
insert into "books" (category_id, title, author, is_adult_only, summary, publisher, published_date, cover_url, view_permission, price, language, total_paragraphs, created_at, updated_at)
values  (1, '만세전', '염상섭', false, '주인공 ''나''는 기차 안에서 정자가 보낸 편지를 읽으며 그녀에 대한 연민과 자신의 태도를 성찰한다. 신호(고베)에 도착한 ''나''는 과거에 만났던 카페의 여급이 동반 자살했다는 소식을 듣고 묘한 충격을 받는다. 이후 음악학교에 다니는 을라를 찾아가 재회하는데, 을라는 반가워하며 기숙사 방으로 그를 안내한다. 대화 도중 을라는 병화의 소식을 전하며 은근히 경제적 도움을 바라는 눈치를 보이고, ''나''는 변해버린 그녀의 모습에 실망감과 거리감을 느낀다. 을라는 서울로 함께 가자고 제안하지만, ''나''는 그녀의 속물적인 태도에 거부감을 느끼며 작별을 고하고 나온다.', null, '1924-08-01 00:00:00.000000', null, 'FREE', 0, 'ko', 556, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '구운몽 (九雲夢) - 상', '미상', false, '육관대사의 제자 성진은 용궁에 심부름을 다녀오다 팔선녀와 마주치며 세속의 부귀영화와 남녀 간의 정을 동경하게 된다. 이에 육관대사는 성진을 인간 세상으로 추방하여 양소유로 환생시킨다. 양소유는 뛰어난 재주와 외모를 지닌 인물로 성장하여 과거를 보러 가는 길에 진채봉, 계섬월 등 여러 여인과 인연을 맺는다. 이후 장원 급제하여 한림학사가 되고, 정사도의 딸 정경패를 얻기 위해 여장하고 거문고를 타는 등 기지를 발휘한다. 또한 국가의 위기 상황에서 출정하여 공을 세우고, 남장한 적경홍과 재회하며, 마침내 황제의 누이인 난양공주의 배필로 지목되는 등 온갖 부귀영화를 누리게 된다.', null, null, null, 'FREE', 0, 'ko', 708, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '구운몽 (九雲夢) - 하', '미상', false, '양소유는 과거에 급제하여 황제의 총애를 받고, 황제의 누이인 난양공주의 부마로 간택되지만 정소저와의 약혼을 이유로 거절하여 옥에 갇힌다. 때마침 토번이 침략하자 대원수가 되어 출정하고, 그 과정에서 자객 심요연과 용왕의 딸 백능파를 만나 인연을 맺고 승전한다. 돌아온 양소유는 황태후와 공주의 계략으로 정소저(영양공주)와 난양공주 두 명을 정실부인으로 맞이하고, 여섯 첩과 함께 부귀영화를 누린다. 말년에 인생의 무상함을 느끼던 중, 옛 스승인 노승을 만나 꿈에서 깨어 다시 성진으로 돌아오며 깨달음을 얻는다.', null, null, null, 'FREE', 0, 'ko', 723, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '날개', '이상', false, '1930년대 지식인의 자아 분열과 무기력함을 다룬 이상의 대표적인 심리 소설이다. 주인공 ''나''는 매춘을 하는 아내에게 기생하며 볕이 들지 않는 33번지의 어두운 방에서 뒹굴며 지낸다. 그는 아내의 방에 손님이 들면 윗방에서 죽은 듯이 있거나 거리를 배회하며, 현실 감각을 잃고 끊임없이 자신의 내부로 침잠한다. 어느 날 아내가 자신에게 수면제인 아달린을 먹여왔다는 의심을 품게 된 ''나''는 집을 뛰쳐나와 미쓰꼬시 백화점 옥상에 오른다. 회색빛 도시를 내려다보던 그는 겨드랑이에서 인공의 날개가 돋았던 자국을 느끼며, 다시 한번 날아오르고자 하는 의지를 다진다.', null, null, null, 'FREE', 0, 'ko', 170, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '만복사저포기(萬福寺樗浦記)', '김시습', false, '전라도 남원에 사는 총각 양생은 만복사에서 부처와 저포 놀이를 하여 이긴 대가로 아름다운 여인을 만납니다. 두 사람은 개령동의 은밀한 처소에서 사흘간 사랑을 나누며 시를 읊고 즐기지만, 사실 여인은 왜구의 침입 때 죽은 귀신이었습니다. 여인의 부모를 만나 그녀의 정체를 알게 된 양생은 여인을 위해 장례와 재를 올려줍니다. 여인은 양생의 정성에 감사하며 타국에서 남자로 환생했다는 소식을 전하고 떠납니다. 이후 양생은 다시 장가들지 않고 지리산에 들어가 자취를 감춥니다.', null, null, null, 'FREE', 0, 'ko', 85, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '무녀도', '김동리', false, '화자인 ''나''는 집안 대대로 내려오는 ''무녀도''라는 그림에 얽힌 내력을 할아버지로부터 전해 듣습니다. 경주 변두리의 퇴락한 집에서 무당 모화와 그녀의 귀먹은 딸 낭이는 세상과 단절된 채 살아갑니다. 모화는 낭이를 용신의 화신이라 믿으며 극진히 보살핍니다. 그러던 어느 날, 절로 보내졌던 모화의 아들 욱이가 십 년 만에 돌아옵니다. 하지만 욱이는 기독교인이 되어 성경을 읽고 기도를 올리는 등 모화의 무속 신앙과 정면으로 충돌합니다. 모화는 아들에게 잡귀가 들렸다고 믿으며 귀신을 쫓으려 하고, 욱이는 성경의 말씀을 전하며 어머니의 신앙을 부정합니다. 결국 모화는 아들에게 깃든 예수를 쫓기 위해 강력한 푸닥거리를 시작하며 모자간의 종교적 대립은 극에 달합니다.', null, null, null, 'FREE', 0, 'ko', 162, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '봄봄', '김유정', false, '데릴사위로 들어와 돈 한 푼 받지 않고 3년 7개월간 일해온 ''나''는 점순이의 키가 미처 자라지 않았다는 이유로 성례를 미루는 장인에게 불만을 느낍니다. 모내기를 하던 중 배가 아프다는 핑계로 일을 거부하자 장인은 뺨을 때리고 욕설을 퍼붓습니다. 결국 ''나''는 성례를 시켜주지 않을 거면 사경(임금)을 내놓으라며 장인을 구장에게 끌고 가려 합니다.', null, '1935-12-01 00:00:00.000000', null, 'FREE', 0, 'ko', 132, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '사씨남정기(謝氏南征記)', '김만중', false, '명나라의 유현(유공)은 아들 유연수가 한림학사가 되자 어진 배필을 구합니다. 매파 주파의 추천과 관음찬 시험을 통해 현숙한 사정옥을 며느리로 맞이합니다. 유공은 사부인의 덕행을 칭찬하며 유언을 남기고 별세합니다. 유한림은 서른이 되도록 자식이 없자 사부인의 간곡한 권유로 양반가 출신의 교채란을 소실로 들이게 되는데, 고모인 두부인은 교씨의 미모 뒤에 숨겨진 성품을 우려하며 불길한 징조를 예견합니다.', null, null, null, 'FREE', 0, 'ko', 752, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '수오재기', '정약용', false, '작가는 큰형님이 집의 이름을 ''수오재(자신을 지키는 집)''라고 지은 이유에 대해 의문을 품게 됩니다. 장기로 유배를 온 후에야 그는 세상 만물 중 가장 잃어버리기 쉽고 지키기 어려운 것이 바로 ''나'' 자신임을 깨닫습니다. 과거 공부와 관직 생활을 하며 자신을 잃고 떠돌았던 지난날을 깊이 반성하는 태도를 보입니다. 반면 본연의 자아를 잃지 않고 수오재에 머물렀던 큰형님의 삶을 예찬하며 수신의 중요성을 설파합니다. 결국 유배지에서 되찾은 ''나''와 함께 머물며 이 깨달음을 기록으로 남기기로 결심합니다.', null, null, null, 'FREE', 0, 'ko', 18, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '숙향전', '미상', false, '송나라의 선비 김전은 죽을 위기에 처한 거북을 구해주고, 후에 거북의 도움으로 목숨을 구하며 신비한 구슬을 얻습니다. 장희의 딸과 성혼한 김전은 태몽 끝에 천상에서 적강한 ''월궁소아'' 숙향을 낳습니다. 그러나 숙향이 다섯 살 되던 해 병란이 일어나 가족이 피란하던 중 도적을 만나게 되고, 부모는 어쩔 수 없이 숙향을 바위틈에 남겨두고 떠납니다. 홀로 남겨진 숙향은 동물들의 도움과 마을 사람들의 배려로 겨우 목숨을 부지하며 부모를 기다립니다.', null, null, null, 'FREE', 0, 'ko', 372, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '장마', '윤흥길', false, '6.25 전쟁 중 장마가 계속되는 어느 여름 밤, 외할머니는 자신의 불길한 꿈이 현실이 될 것임을 직감하며 불안해한다. 완두를 까며 끝없이 꿈 이야기를 늘어놓는 외할머니와 달리, 어머니와 이모는 국군 소대장으로 나간 외삼촌 길준의 무사함을 믿으려 애쓴다. 마을 개들이 유난히 짖어대던 중 구장과 방수포를 쓴 사내들이 찾아와 외삼촌의 전사 소식을 전하는 통지서를 건넨다. 비극적인 소식 앞에 어머니는 절규하며 오열하지만, 외할머니는 자신의 예언이 적중했다는 사실에 묘한 승리감과 기괴한 흥분을 느낀다. 화자인 ''나(동만)''는 이 참혹한 상황 속에서 우연히 잡은 벌레인 ''하늘밥도둑''의 생사를 쥐고 묘한 권력감을 느끼며 집안의 비극을 지켜본다.', null, null, null, 'FREE', 0, 'ko', 316, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '장화홍련전', '미상', false, '평안도 철산군의 배무룡 좌수는 장화와 홍련이라는 두 자매를 얻지만, 부인 장씨가 일찍 세상을 떠납니다. 배좌수는 후처 허씨를 얻고 세 아들을 낳지만, 허씨는 전처의 딸들을 시기하여 죽일 음모를 꾸밉니다. 허씨는 쥐를 이용해 장화가 낙태를 한 것처럼 꾸며 배좌수를 속이고, 이를 믿은 좌수는 아들 장쇠를 시켜 장화를 연못에 빠뜨려 죽게 합니다. 장화가 죽자 하늘이 노하여 호랑이가 나타나 장쇠의 몸을 훼손하는 천벌을 내립니다. 언니를 잃은 홍련은 깊은 슬픔에 빠져 진실을 쫓으며, 꿈속에서 신령한 존재가 된 장화를 만나 불길한 징조를 확인합니다. 결국 자매의 억울한 원혼은 이 사건의 전말을 세상에 알리기 시작합니다.', null, null, null, 'FREE', 0, 'ko', 136, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '탁류(濁流)', '채만식', false, '금강이 흘러 닿는 항구 도시 군산. 몰락한 양반 출신의 정주사는 미두장(쌀 거래소) 주변을 맴도는 ''하바꾼'' 신세로 전락했다. 그는 돈 없이 미두를 하려다 젊은 애송이에게 멱살을 잡히고 봉변을 당하지만, 은행원 고태수의 개입으로 풀려난다. 가난과 빚, 많은 식구들 걱정에 자살 충동까지 느끼는 정주사는 쌀이라도 외상으로 얻어볼 요량으로 탑삭부리 한참봉의 쌀가게를 찾는다. 그곳에서 한참봉의 아내 김씨는 정주사의 딸 초봉이의 미모를 칭찬하며 중매를 서겠다고 제안하고, 정주사는 씁쓸한 현실 속에서도 그들과 장기를 두며 잠시 시름을 잊는다.', null, '1937-10-01 00:00:00.000000', null, 'FREE', 0, 'ko', 1327, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '토끼전', '미상', false, '별주부 자라에게 속아 수궁으로 잡혀온 토끼는 용왕으로부터 병을 고치기 위해 간을 내놓으라는 엄명을 듣습니다. 절체절명의 위기 속에서 토끼는 자신의 간이 영약이라 산속 깊은 곳에 씻어 감추어 두고 왔다는 기지를 발휘합니다. 처음에는 의심하던 용왕도 토끼의 태연한 태도와 달마다 간을 출입한다는 감언이설에 속아 넘어가고, 간신 자가사리의 반대에도 불구하고 토끼를 극진히 대접하며 다시 육지로 보내기로 결정합니다.', null, null, null, 'FREE', 0, 'ko', 69, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '허생전(許生傳)', '박지원', false, '남산 밑 묵적골에 살며 글읽기에만 매진하던 허생은 가난을 견디다 못한 아내의 냉소와 꾸짖음에 10년 기약 중 7년 만에 책을 덮고 집을 나섭니다. 한양 제일의 부자 변씨를 찾아간 허생은 아무런 담보도 없이 단도직입적으로 만 냥을 빌려달라고 청합니다. 변씨는 허생의 비범한 기상을 알아보고 흔쾌히 거금을 내주어 주위 사람들을 놀라게 합니다.', null, null, null, 'FREE', 0, 'ko', 133, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160'),
        (1, '김씨환생전', 'HOT8OY', false, '대한민국의 평범한 30대 IT 개발자 김민준은 살인적인 야근 끝에 정신을 잃는다. 다시 눈을 떴을 때, 그는 익숙한 천장이 아닌 서까래가 보이는 낯선 한옥에 누워 있었다. 상황을 파악하기도 전에 머슴 돌쇠가 들어와 그를 ''도련님''이라 부르며 깨운다. 거울 대신 물동이에 비친 자신의 얼굴이 수려한 외모의 조선시대 양반 ''김선우''임을 확인한 그는 경악한다. 하지만 곧 자신이 대감 댁의 금지옥엽 외아들이라는 사실과 엄청난 부자라는 것을 깨닫고, 현대의 지식을 이용해 조선에서 ''꿀 빠는'' 인생을 살기로 결심한다.', null, null, 'https://drive.google.com/file/d/1mq0Nv-jHAKYP5wXD5jyNt4hAmVeKY4S5/view?usp=sharing', 'PREMIUM', 4900, 'ko', 86, '2026-02-03 05:28:33.477160', '2026-02-03 05:28:33.477160');

-- [10] Book Vectors (도서 벡터 - Book 참조)
INSERT INTO "book_vectors" ("book_id", "vector", "created_at") VALUES
                                                                   (1, array_fill(0, ARRAY[1024])::halfvec(1024), now()),
                                                                   (2, array_fill(0, ARRAY[1024])::halfvec(1024), now()),
                                                                   (3, array_fill(0, ARRAY[1024])::halfvec(1024), now());

-- [11] Chapters (챕터 정보 - Book 참조) 260203 수정
insert into "chapters" (book_id, chapter_name, sequence, book_content_path, created_at, updated_at, is_embedded, paragraphs)
values  (1, '만세전 1장', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/13ef00fa-1_%EB%A7%8C%EC%84%B8%EC%A0%84_chapter1.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 209),
        (1, '만세전 2장', 2, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/2f87034b-e_%EB%A7%8C%EC%84%B8%EC%A0%84_chapter2.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 163),
        (1, '만세전 3장', 3, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/550e447e-8_%EB%A7%8C%EC%84%B8%EC%A0%84_chapter3.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 139),
        (1, '만세전 4장', 4, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/70d0c2f0-8_%EB%A7%8C%EC%84%B8%EC%A0%84_chapter4.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 45),
        (2, '구운몽 (九雲夢)', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/8d2561b1-0_%EA%B5%AC%EC%9A%B4%EB%AA%BD%20%28%E4%B9%9D%E9%9B%B2%E5%A4%A2%29_%EC%83%81.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 708),
        (3, '구운몽 (九雲夢)', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/60d75f17-0_%EA%B5%AC%EC%9A%B4%EB%AA%BD%20%28%E4%B9%9D%E9%9B%B2%E5%A4%A2%29_%ED%95%98.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 723),
        (4, '날개', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/2e972c4f-2_%EB%82%A0%EA%B0%9C.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 170),
        (5, '만복사저포기(萬福寺樗浦記)', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/d85d4501-b_%EB%A7%8C%EB%B3%B5%EC%82%AC%EC%A0%80%ED%8F%AC%EA%B8%B0.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 85),
        (6, '무녀도 1장', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/75ff64a0-2_%EB%AC%B4%EB%85%80%EB%8F%84_1.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 76),
        (6, '무녀도 2장', 2, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/d35eaf5b-9_%EB%AC%B4%EB%85%80%EB%8F%84_2.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 56),
        (6, '무녀도 3장', 3, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/91fb02f9-5_%EB%AC%B4%EB%85%80%EB%8F%84_3.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 30),
        (7, '자라지 않는 키와 데릴사위의 울화', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/b906ee31-d_%EB%B4%84%EB%B4%84%20%EC%B1%95%ED%84%B01.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 40),
        (7, '점순이의 충동질과 구장님의 판결', 2, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/fc546610-0_%EB%B4%84%EB%B4%84%20%EC%B1%95%ED%84%B02.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 35),
        (7, '수염을 잡아챈 싸움과 알 수 없는 속마음', 3, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/6a9403fd-2_%EB%B4%84%EB%B4%84%20%EC%B1%95%ED%84%B03.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 57),
        (8, '천정연분과 가문의 위기', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/01f40dbd-9_%EC%82%AC%EC%94%A8%EB%82%A8%EC%A0%95%EA%B8%B0%20%EC%B1%95%ED%84%B01.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 111),
        (8, '교씨의 요악과 옥지환의 음모', 2, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/cd034f68-9_%EC%82%AC%EC%94%A8%EB%82%A8%EC%A0%95%EA%B8%B0%20%EC%B1%95%ED%84%B02.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 150),
        (8, '골육의 참변과 사부인의 남행', 3, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/c11d8d40-0_%EC%82%AC%EC%94%A8%EB%82%A8%EC%A0%95%EA%B8%B0%20%EC%B1%95%ED%84%B03.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 86),
        (8, '악양루의 신비로운 가호와 수월암의 안식', 4, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/c4b3a1cb-8_%EC%82%AC%EC%94%A8%EB%82%A8%EC%A0%95%EA%B8%B0%20%EC%B1%95%ED%84%B04.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 84),
        (8, '한림의 적거(謫居)와 동청의 득세', 5, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/a8e7baa1-b_%EC%82%AC%EC%94%A8%EB%82%A8%EC%A0%95%EA%B8%B0%20%EC%B1%95%ED%84%B05.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 100),
        (8, '진실의 탄로와 백빈주의 해후', 6, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/6c2c96ed-b_%EC%82%AC%EC%94%A8%EB%82%A8%EC%A0%95%EA%B8%B0%20%EC%B1%95%ED%84%B06.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 95),
        (8, '악인의 파멸과 사부인의 귀환', 7, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/e089295b-e_%EC%82%AC%EC%94%A8%EB%82%A8%EC%A0%95%EA%B8%B0%20%EC%B1%95%ED%84%B07.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 61),
        (8, '인아와의 상봉과 권선징악의 대단원', 8, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/ebde632b-f_%EC%82%AC%EC%94%A8%EB%82%A8%EC%A0%95%EA%B8%B0%20%EC%B1%95%ED%84%B08.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 65),
        (9, '수오재기', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/a51b5793-d_%EC%88%98%EC%98%A4%EC%9E%AC%EA%B8%B0.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 18),
        (10, '김전의 보은과 숙향의 탄생', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/6c41aa3d-6_%EC%88%99%ED%96%A5%EC%A0%84%20%EC%B1%95%ED%84%B01.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 54),
        (10, '명사계의 인도와 장승상 댁의 누명', 2, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/109f86e4-e_%EC%88%99%ED%96%A5%EC%A0%84%20%EC%B1%95%ED%84%B02.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', false, 112),
        (10, '천상의 조력과 마고할미와의 인연', 3, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/e88b8d28-b_%EC%88%99%ED%96%A5%EC%A0%84%20%EC%B1%95%ED%84%B03.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', false, 116),
        (10, '요지경의 꿈과 인연의 증표', 4, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/c4d88edf-3_%EC%88%99%ED%96%A5%EC%A0%84%20%EC%B1%95%ED%84%B04.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 90),
        (11, '장마 1장', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/8967f1ef-c_%EC%9E%A5%EB%A7%88_1.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 72),
        (11, '장마 2장', 2, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/813416a8-6_%EC%9E%A5%EB%A7%88_2.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 43),
        (11, '장마 3장', 3, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/3f849a6e-5_%EC%9E%A5%EB%A7%88_3.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 130),
        (11, '장마 4장', 4, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/e16f3bd9-c_%EC%9E%A5%EB%A7%88_4.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 20),
        (11, '장마 5장', 5, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/e2abf63a-7_%EC%9E%A5%EB%A7%88_5.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 20),
        (11, '장마 6장', 6, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/5e902f1d-7_%EC%9E%A5%EB%A7%88_6.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 31),
        (12, '장화홍련전 1장', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/05bb9407-4_%EC%9E%A5%EB%A7%88%ED%99%8D%EB%A0%A8%EC%A0%84_1.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 105),
        (12, '장화홍련전 2장', 2, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/98d7c51b-6_%EC%9E%A5%EB%A7%88%ED%99%8D%EB%A0%A8%EC%A0%84_2.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 31),
        (13, '인간기념물', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/dd16d95b-1_%ED%83%81%EB%A5%98%28%E6%BF%81%E6%B5%81%29_chapter1.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 237),
        (13, '생활 제일과(第一課)', 2, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/d592648d-d_%ED%83%81%EB%A5%98%28%E6%BF%81%E6%B5%81%29_chapter2.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 375),
        (13, '신판 흥부전(新版 興甫傳)', 3, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/d0865ed1-c_%ED%83%81%EB%A5%98%28%E6%BF%81%E6%B5%81%29_chapter3.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 379),
        (13, '생애는 방안지라', 4, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/42062067-2_%ED%83%81%EB%A5%98%28%E6%BF%81%E6%B5%81%29_chapter4.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 336),
        (14, '수궁의 위기와 토끼의 기지', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/79bcf11a-c_%ED%86%A0%EB%81%BC%EC%A0%84%20%EC%B1%95%ED%84%B01.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 48),
        (14, '토끼의 조롱과 별주부의 고충', 2, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/0c33093a-c_%ED%86%A0%EB%81%BC%EC%A0%84%20%EC%B1%95%ED%84%B02.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 21),
        (15, '글공부를 그만두고 장사를 시작하다', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/0ea6d02b-9_%ED%97%88%EC%83%9D%EC%A0%84%20%EC%B1%95%ED%84%B01.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 27),
        (15, '매점매석과 빈 섬의 경영', 2, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/6b053f6c-8_%ED%97%88%EC%83%9D%EC%A0%84%20%EC%B1%95%ED%84%B02.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 48),
        (15, '변씨와의 재회와 비범한 통찰', 3, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/18772d68-b_%ED%97%88%EC%83%9D%EC%A0%84%20%EC%B1%95%ED%84%B03.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 30),
        (15, '이완 대장과의 만남과 허생의 종적', 4, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/4b089b43-d_%ED%97%88%EC%83%9D%EC%A0%84%20%EC%B1%95%ED%84%B04.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 28),
        (16, '야근 끝에 조선이라니', 1, 'https://readsync-storage-v1.s3.ap-northeast-2.amazonaws.com/book/e3fa937c-2_%EA%B9%80%EC%94%A8%ED%99%98%EC%83%9D%EC%A0%84.json', '2026-02-03 05:29:02.601305', '2026-02-03 05:29:02.601305', true, 86);

-- [12] Chapter Vectors (챕터 벡터 - Chapter 참조)
INSERT INTO "chapter_vectors" ("chapter_id", "vector", "created_at") VALUES
                                                                         (1, array_fill(0, ARRAY[1024])::halfvec(1024), now()),
                                                                         (2, array_fill(0, ARRAY[1024])::halfvec(1024), now()),
                                                                         (3, array_fill(0, ARRAY[1024])::halfvec(1024), now()),
                                                                         (4, array_fill(0, ARRAY[1024])::halfvec(1024), now()),
                                                                         (5, array_fill(0, ARRAY[1024])::halfvec(1024), now());

-- [13] Libraries (서재/소장 - User, Book 참조)
INSERT INTO "libraries" ("user_id", "book_id", "type", "reading_status") VALUES
                                                                             (2, 1, 'OWNED', 'READING'),
                                                                             (2, 2, 'OWNED', 'COMPLETED'),
                                                                             (3, 1, 'OWNED', 'READING'),
                                                                             (4, 3, 'OWNED', 'READING');

-- [14] Bookmarks (북마크/진척도 - Chapter, Library 참조)
INSERT INTO "bookmarks" ("chapter_id", "last_read_pos", "progress", "library_id", "read_mask") VALUES
                                                                                                   (1, 6, 50.0, 1, '\x11111110000000'),
                                                                                                   (3, 0, 0.0, 2, '\x00000000000000'),
                                                                                                   (5, 0, 0.0, 4, '\x00000000000000');

-- [15] Book Logs (독서 로그 - Library 참조)
INSERT INTO "book_logs" ("read_paragraph", "read_time", "library_id") VALUES
                                                                          (5, 100, 1),
                                                                          (0, 0, 2),
                                                                          (0, 0, 4);

-- [16] Credit Type
-- user_id 제거, 컬럼명 명확화
INSERT INTO "credit_type" ("base_expiry_days", "credit_name") VALUES
                                                                  (30, '이벤트 포인트'),  -- 1
                                                                  (365, '유료 크레딧'),   -- 2
                                                                  (90, '보상 포인트'),    -- 3
                                                                  (365, '구독 혜택');     -- 4

-- [17] Credits
-- user_id 추가, expired_at 추가, credits -> amount 변경
INSERT INTO "credits" ("user_id", "credit_type_id", "amount", "status", "created_at", "expired_at") VALUES
                                                                                                        (2, 1, 1000, 'ACTIVE', now(), now() + interval '30 days'),
                                                                                                        (2, 2, 5000, 'ACTIVE', now(), now() + interval '365 days'),
                                                                                                        (3, 1, 500, 'ACTIVE', now(), now() + interval '30 days');
-- [18] Payment Methods (결제 수단 - User 참조)
INSERT INTO "payment_methods" ("user_id", "billing_key", "pg_provider", "card_company", "card_last_4", "created_at") VALUES
                                                                                                                         (2, 'bill_key_user2', 'TOSS', 'SHINHAN', '1234', now()),
                                                                                                                         (3, 'bill_key_user3', 'KAKAO', 'KOOKMIN', '5678', now()),
                                                                                                                         (4, 'bill_key_user4', 'NAVER', 'HYUNDAI', '9012', now());

-- [19] Subscription Plans (구독 플랜 정보)
INSERT INTO "subscription_plans" ("plan_name", "price", "description", "give_credit", "is_active") VALUES
                                                                                                       ('베이직', 5900, '기본적인 독서 기능을 제공합니다.', 1000, TRUE),    -- 1
                                                                                                       ('프리미엄', 9900, '모든 독서 기능과 추가 혜택을 제공합니다.', 2200, TRUE), -- 2
                                                                                                       ('패밀리', 14900, '가족과 함께 즐기는 독서 라이프.', 3400, TRUE);      -- 3

-- [19-1] Subscriptions (구독 정보 - User, Plan 참조)
-- started_at 추가됨
INSERT INTO "subscriptions" ("plan_id", "status", "next_billing_date", "user_id", "started_at") VALUES
                                                                                                    (2, 'ACTIVE', now() + interval '1 month', 2, now()),  -- 프리미엄
                                                                                                    (1, 'EXPIRED', now() - interval '1 day', 3, now() - interval '1 month'),   -- 베이직
                                                                                                    (3, 'ACTIVE', now() + interval '15 days', 4, now() - interval '15 days');  -- 패밀리

-- [20] Carts (장바구니 - User, Book 참조)
INSERT INTO "carts" ("user_id", "book_id", "quantity") VALUES
                                                           (2, 3, 1),
                                                           (3, 2, 1),
                                                           (4, 1, 1);

-- [21] Orders (주문 내역 - User, Payment Method, Subscription, Cart 참조)
INSERT INTO "orders" ("user_id", "method_id", "order_uid", "order_name", "total_amount", "status") VALUES
                                                                                                       (2, 1, 'ORD-001', '데이터베이스의 이해 외', 25000, 'COMPLETED'),
                                                                                                       (3, 2, 'ORD-002', '자바 프로그래밍 마스터', 32000, 'COMPLETED'),
                                                                                                       (4, 3, 'ORD-003', 'AI와 미래 사회', 18000, 'PENDING');

-- [22] Order Items (주문 상세 상품 - Book, Order 참조)
INSERT INTO "order_items" ("book_id", "order_id", "snapshot_price", "quantity", "status") VALUES
                                                                                              (1, 1, 25000, 1, 'ORDER_COMPLETED'),
                                                                                              (2, 2, 32000, 1, 'ORDER_COMPLETED'),
                                                                                              (3, 3, 18000, 1, 'PARTIAL_CANCELED');

-- [23] Payment History (결제 이력 - Order 참조)
INSERT INTO "payment_history" ("pg_payment_key", "amount", "status", "pg_provider", "created_at", "order_id") VALUES
                                                                                                                  ('pg_key_1', 25000, 'DONE', 'TOSS', now(), 1),
                                                                                                                  ('pg_key_2', 32000, 'DONE', 'KAKAO', now(), 2),
                                                                                                                  ('pg_key_3', 18000, 'WAITING', 'NAVER', now(), 3);

-- [24] Community Posts (게시글 - User 참조)
INSERT INTO "community_posts" ("title", "content", "user_id") VALUES
                                                                  ('오늘의 추천도서', 'DB 책 꼭 보세요.', 2),
                                                                  ('같이 읽을 분 구해요', '주말 오전반 모집합니다.', 3),
                                                                  ('AI 관련 질문있습니다', '미래 사회 책 읽어보신 분?', 4);

-- [25] Community Comments (게시글 댓글 - User, Post 참조)
INSERT INTO "community_comments" ("content", "user_id", "post_id") VALUES
                                                                       ('감사합니다!', 3, 1),
                                                                       ('저 참여할게요.', 2, 2),
                                                                       ('저도 궁금하네요.', 2, 3);

-- [26] Notices (공지사항 - User(Admin) 참조)
INSERT INTO "notices" ("title", "content", "user_id") VALUES
                                                          ('서버 점검 안내', '새벽 2시부터 4시까지 점검입니다.', 1),
                                                          ('신규 가입 이벤트', '웰컴 포인트를 드립니다.', 1),
                                                          ('약관 변경 안내', '개인정보 처리방침이 변경됩니다.', 1);

-- [27] Inquiries (1:1 문의 - User 참조)
INSERT INTO "inquiries" ("title", "user_id", "content") VALUES
                                                            ('결제 취소 문의', 2, '실수로 잘못 결제했어요.'),
                                                            ('로그인이 안돼요', 3, '비밀번호 찾기가 안됩니다.'),
                                                            ('책 내용 오류 신고', 4, '오타를 발견했습니다.');

-- [28] Inquiry Answers (문의 답변 - User(Admin), Inquiry 참조)
INSERT INTO "inquiry_answers" ("content", "admin_user_id", "inquiry_id") VALUES
                                                                       ('취소 처리 해드렸습니다.', 1, 1),
                                                                       ('임시 비밀번호를 발송했습니다.', 1, 2),
                                                                       ('소중한 제보 감사합니다.', 1, 3);

-- [29] Friendships (친구 관계 - User 참조)
INSERT INTO "friendships" ("status", "requester_id", "addressee_id") VALUES
                                                                         ('ACCEPTED', 2, 3),
                                                                         ('PENDING', 1, 2),
                                                                         ('ACCEPTED', 3, 4);

-- [30] Reading Rooms (독서실 - Host(User), Library 참조)
INSERT INTO "reading_rooms" ("host_id", "library_id", "room_name", "current_chapter") VALUES
                                                                                          (2, 1, '데이터베이스 빡독방', 1),
                                                                                          (3, 2, '자바 스터디', 1),
                                                                                          (4, 3, 'AI 독서 모임', 1);

-- [31] Room Participants (독서실 참여자 - Room, User 참조)
INSERT INTO "room_participants" ("room_id", "user_id", "connection_status") VALUES
                                                                                (1, 2, 'ACTIVE'),
                                                                                (1, 3, 'ACTIVE'),
                                                                                (2, 4, 'ACTIVE');

-- [32] Chat Logs (독서실 채팅 - Room, User 참조)
INSERT INTO "chat_logs" ("room_id", "user_id", "content") VALUES
                                                              (1, 2, '안녕하세요! 1장부터 읽으시죠.'),
                                                              (1, 3, '네 반갑습니다.'),
                                                              (2, 4, '혼자 하려니 심심하네요.');

-- [33] Room Invitations (독서실 초대 - Room, User 참조)
INSERT INTO "room_invitations" ("room_id", "status", "receiver_id", "sender_id") VALUES
                                                                                     (1, 'ACCEPTED', 3, 2),
                                                                                     (2, 'PENDING', 2, 3),
                                                                                     (1, 'REJECTED', 4, 2);

-- [34] Reports (채팅 신고 - User, ChatLog 참조)
-- INSERT INTO "reports" ("reporter_id", "chat_id", "reason") VALUES
--                                                                (3, 1, '부적절한 대화 내용'),
--                                                                (2, 3, '광고성 메시지'),
--                                                                (4, 2, '욕설 사용');
INSERT INTO "reports" ("reporter_id", "target_user_id", "chat_id", "reason", "reported_content") VALUES
                                                                                                     (3, 2, 1, '부적절한 대화 내용', '안녕하세요! 1장부터 읽으시죠.'),
                                                                                                     (2, 4, 3, '광고성 메시지', '혼자 하려니 심심하네요.'), -- 3번 채팅은 user 4가 작성
                                                                                                     (4, 3, 2, '욕설 사용', '네 반갑습니다.'); -- 2번 채팅은 user 3가 작성

-- [35] Book AI Chat Rooms (AI 채팅방 - User, Chapter 참조)
INSERT INTO "book_ai_chat_rooms" ("user_id", "chapter_id", "title") VALUES
                                                                        (2, 1, 'DB 정규화 질문'),
                                                                        (3, 3, '자바 객체지향 개념'),
                                                                        (4, 5, 'AI 윤리 문제');

-- [36] Book AI Chats (AI 대화 - AI Room, User 참조)
INSERT INTO "book_ai_chats" ("ai_room_id", "user_id", "user_msg", "ai_msg") VALUES
                                                                                (1, 2, '제2정규형이 뭐야?', '부분 함수 종속을 제거한 것입니다.'),
                                                                                (2, 3, '다형성이란?', '하나의 인터페이스로 다양한 구현을 갖는 것입니다.'),
                                                                                (3, 4, 'AI가 일자리를 뺏을까?', '일부 대체되겠지만 새로운 직업도 생길 것입니다.');

-- [37] Reviews (도서 리뷰 - User, Book 참조)
INSERT INTO "reviews" ("user_id", "book_id", "rating", "review_content", "visibility_status") VALUES
                                                                                                  (2, 1, 5, '최고의 DB 입문서!', 'ACTIVE'),
                                                                                                  (3, 1, 4, '내용은 좋은데 조금 어렵네요.', 'ACTIVE'),
                                                                                                  (4, 2, 3, '예제가 좀 옛날 방식인 듯.', 'ACTIVE');

-- [38] Comments (챕터 코멘트 - User, Chapter 참조)
INSERT INTO "comments" ("user_id", "chapter_id", "comment_content", "visibility_status") VALUES
                                                                                             (2, 1, '이 부분 설명이 아주 좋습니다.', 'ACTIVE'),
                                                                                             (3, 2, '이해가 잘 안되네요.', 'ACTIVE'),
                                                                                             (4, 3, '코드 따라 치기 좋아요.', 'ACTIVE');

-- [39] Likes (좋아요 - Review, User 참조)
INSERT INTO "likes" ("review_id", "user_id", "reaction_type") VALUES
                                                                  (1, 3, 'LIKE'),
                                                                  (2, 2, 'LIKE'),
                                                                  (3, 4, 'DISLIKE');

-- [40] Content Reports (콘텐츠 신고 - Review, User 참조)
INSERT INTO "content_reports" ("review_id", "user_id", "target_type", "reason_type") VALUES
                                                                                         (2, 3, 'REVIEW', 'SPOILER'),
                                                                                         (1, 4, 'REVIEW', 'ADVERTISEMENT'),
                                                                                         (3, 2, 'REVIEW', 'ABUSE');

-- [41] Exp Logs (경험치 로그 - User, Exp Rule 참조)
INSERT INTO "exp_logs" ("user_id", "exp_rule_id", "earned_exp", "target_id", "reference_id") VALUES
                                                                                                 (2, 1, 10, 0, 0), -- 로그인
                                                                                                 (2, 3, 100, 1, 1), -- 리뷰 작성
                                                                                                 (3, 2, 50, 1, 1); -- 챕터 읽기


-- [42] rag_parent_documents 260203 수정
