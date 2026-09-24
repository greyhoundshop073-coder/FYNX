import express from "express";
// Profile-photo media access follows the owner's profile visibility and block state.
// Chat hardening keeps profile-photo visibility aligned with authenticated users.
import http from "http";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import pg from "pg";