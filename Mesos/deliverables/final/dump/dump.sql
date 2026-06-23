


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


CREATE SCHEMA IF NOT EXISTS "public";


ALTER SCHEMA "public" OWNER TO "pg_database_owner";


COMMENT ON SCHEMA "public" IS 'standard public schema';


SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "public"."match_participations" (
    "match_id" integer NOT NULL,
    "player_id" integer NOT NULL,
    "final_score" integer NOT NULL
);


ALTER TABLE "public"."match_participations" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."matches" (
    "match_id" integer NOT NULL,
    "match_date" timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "player_count" integer NOT NULL
);


ALTER TABLE "public"."matches" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."players" (
    "player_id" integer NOT NULL,
    "nickname" character varying(50) NOT NULL
);


ALTER TABLE "public"."players" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."players_player_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."players_player_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "public"."players_player_id_seq" OWNED BY "public"."players"."player_id";



ALTER TABLE ONLY "public"."players" ALTER COLUMN "player_id" SET DEFAULT "nextval"('"public"."players_player_id_seq"'::"regclass");



ALTER TABLE ONLY "public"."match_participations"
    ADD CONSTRAINT "match_participations_pkey" PRIMARY KEY ("match_id", "player_id");



ALTER TABLE ONLY "public"."matches"
    ADD CONSTRAINT "matches_pkey" PRIMARY KEY ("match_id");



ALTER TABLE ONLY "public"."players"
    ADD CONSTRAINT "players_nickname_key" UNIQUE ("nickname");



ALTER TABLE ONLY "public"."players"
    ADD CONSTRAINT "players_pkey" PRIMARY KEY ("player_id");



ALTER TABLE ONLY "public"."match_participations"
    ADD CONSTRAINT "match_participations_match_id_fkey" FOREIGN KEY ("match_id") REFERENCES "public"."matches"("match_id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."match_participations"
    ADD CONSTRAINT "match_participations_player_id_fkey" FOREIGN KEY ("player_id") REFERENCES "public"."players"("player_id") ON DELETE CASCADE;



GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";



GRANT ALL ON TABLE "public"."match_participations" TO "anon";
GRANT ALL ON TABLE "public"."match_participations" TO "authenticated";
GRANT ALL ON TABLE "public"."match_participations" TO "service_role";



GRANT ALL ON TABLE "public"."matches" TO "anon";
GRANT ALL ON TABLE "public"."matches" TO "authenticated";
GRANT ALL ON TABLE "public"."matches" TO "service_role";



GRANT ALL ON TABLE "public"."players" TO "anon";
GRANT ALL ON TABLE "public"."players" TO "authenticated";
GRANT ALL ON TABLE "public"."players" TO "service_role";



GRANT ALL ON SEQUENCE "public"."players_player_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."players_player_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."players_player_id_seq" TO "service_role";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "service_role";







